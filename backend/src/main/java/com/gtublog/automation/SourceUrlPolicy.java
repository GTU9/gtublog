package com.gtublog.automation;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SourceUrlPolicy {

    private static final int MAX_SOURCE_URL_LENGTH = 512;

    private final Set<String> allowedPrivateHosts;
    private final AddressResolver addressResolver;

    @Autowired
    public SourceUrlPolicy(
            @Value("${app.automation.collection.allowed-private-hosts:}") String allowedPrivateHosts) {
        this(parseAllowedPrivateHosts(allowedPrivateHosts), InetAddress::getAllByName);
    }

    SourceUrlPolicy(Set<String> allowedPrivateHosts, AddressResolver addressResolver) {
        this.allowedPrivateHosts = allowedPrivateHosts.stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.addressResolver = addressResolver;
    }

    public URI validateStoredUrl(String rawUrl) {
        var uri = parseAndValidateStructure(rawUrl);
        if (!isExplicitlyAllowed(uri.getHost()) && isLocalHostname(uri.getHost())) {
            throw new IllegalArgumentException("Source URLs must not target local or private network addresses.");
        }
        if (!isExplicitlyAllowed(uri.getHost()) && looksLikeIpLiteral(uri.getHost())) {
            validateAddresses(resolve(uri.getHost()));
        }
        return uri;
    }

    public URI validateFetchUrl(String rawUrl) {
        return resolveFetchUrl(rawUrl).uri();
    }

    public ResolvedSourceUrl resolveFetchUrl(String rawUrl) {
        var uri = parseAndValidateStructure(rawUrl);
        InetAddress[] addresses = resolve(uri.getHost());
        if (!isExplicitlyAllowed(uri.getHost())) {
            if (isLocalHostname(uri.getHost())) {
                throw new IllegalArgumentException("Source URLs must not target local or private network addresses.");
            }
            validateAddresses(addresses);
        }
        return new ResolvedSourceUrl(uri, List.of(addresses));
    }

    private URI parseAndValidateStructure(String rawUrl) {
        try {
            var uri = URI.create(rawUrl == null ? "" : rawUrl.trim());
            var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme))) {
                throw new IllegalArgumentException("Source URLs must use HTTP or HTTPS.");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("Source URLs must include a valid host.");
            }
            if (uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException("Source URLs must not contain user information.");
            }
            if (uri.getPort() < -1 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("Source URLs contain an invalid port.");
            }
            if (uri.toASCIIString().length() > MAX_SOURCE_URL_LENGTH) {
                throw new IllegalArgumentException("Source URL exceeds the allowed length.");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Source URLs")) {
                throw exception;
            }
            throw new IllegalArgumentException("Source URL is invalid.");
        }
    }

    private InetAddress[] resolve(String host) {
        try {
            var addresses = addressResolver.resolve(host);
            if (addresses.length == 0) {
                throw new IllegalArgumentException("Source URL host could not be resolved.");
            }
            return addresses;
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("Source URL host could not be resolved.");
        }
    }

    private void validateAddresses(InetAddress[] addresses) {
        for (var address : addresses) {
            if (!isPublicAddress(address)) {
                throw new IllegalArgumentException("Source URLs must not target local or private network addresses.");
            }
        }
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return !(first == 0
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0 && third == 0)
                    || (first == 192 && second == 0 && third == 2)
                    || (first == 198 && (second == 18 || second == 19))
                    || (first == 198 && second == 51 && third == 100)
                    || (first == 203 && second == 0 && third == 113)
                    || first >= 240);
        }

        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        boolean uniqueLocal = (first & 0xfe) == 0xfc;
        boolean ipv4Compatible = matchesPrefix(bytes, new int[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 96);
        boolean nat64WellKnown = matchesPrefix(bytes, new int[] {0x00, 0x64, 0xff, 0x9b, 0, 0, 0, 0, 0, 0, 0, 0}, 96);
        boolean nat64Local = matchesPrefix(bytes, new int[] {0x00, 0x64, 0xff, 0x9b, 0x00, 0x01}, 48);
        boolean discardOnly = matchesPrefix(bytes, new int[] {0x01, 0x00, 0, 0, 0, 0, 0, 0}, 64);
        boolean teredo = matchesPrefix(bytes, new int[] {0x20, 0x01, 0, 0}, 32);
        boolean benchmarking = matchesPrefix(bytes, new int[] {0x20, 0x01, 0, 0x02, 0, 0}, 48);
        boolean documentation = matchesPrefix(bytes, new int[] {0x20, 0x01, 0x0d, 0xb8}, 32)
                || matchesPrefix(bytes, new int[] {0x3f, 0xff, 0}, 20);
        boolean sixToFour = first == 0x20 && second == 0x02;
        return !(uniqueLocal
                || ipv4Compatible
                || nat64WellKnown
                || nat64Local
                || discardOnly
                || teredo
                || benchmarking
                || documentation
                || sixToFour);
    }

    private boolean matchesPrefix(byte[] address, int[] prefix, int prefixLength) {
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;
        for (int index = 0; index < fullBytes; index++) {
            if (Byte.toUnsignedInt(address[index]) != prefix[index]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xff << (8 - remainingBits);
        return (Byte.toUnsignedInt(address[fullBytes]) & mask) == (prefix[fullBytes] & mask);
    }

    private boolean isLocalHostname(String host) {
        var normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized) || normalized.endsWith(".localhost");
    }

    private boolean isExplicitlyAllowed(String host) {
        return allowedPrivateHosts.contains(host.toLowerCase(Locale.ROOT));
    }

    private static Set<String> parseAllowedPrivateHosts(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(host -> !host.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean looksLikeIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.chars().allMatch(character -> Character.isDigit(character) || character == '.');
    }

    @FunctionalInterface
    interface AddressResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    public record ResolvedSourceUrl(URI uri, List<InetAddress> addresses) {
    }
}
