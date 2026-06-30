package com.gtublog.automation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PinnedSourceHttpClient {

    private static final int MAX_HEADER_BYTES = 64 * 1024;
    private static final int MAX_LINE_BYTES = 8 * 1024;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final SSLSocketFactory sslSocketFactory;

    @Autowired
    public PinnedSourceHttpClient() {
        this(Duration.ofSeconds(5), Duration.ofSeconds(10), (SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    PinnedSourceHttpClient(Duration connectTimeout, Duration requestTimeout) {
        this(connectTimeout, requestTimeout, (SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    PinnedSourceHttpClient(
            Duration connectTimeout,
            Duration requestTimeout,
            SSLSocketFactory sslSocketFactory) {
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
        this.sslSocketFactory = sslSocketFactory;
    }

    public SourceHttpResponse get(SourceUrlPolicy.ResolvedSourceUrl target) throws IOException {
        return get(target, startDeadline());
    }

    RequestDeadline startDeadline() {
        return new RequestDeadline(System.nanoTime() + requestTimeout.toNanos());
    }

    SourceHttpResponse get(SourceUrlPolicy.ResolvedSourceUrl target, RequestDeadline deadline) throws IOException {
        IOException lastFailure = null;
        for (var address : target.addresses()) {
            try {
                return getFromAddress(target.uri(), address.getHostAddress(), deadline.deadlineNanos());
            } catch (IOException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure == null ? new IOException("Source host did not resolve to an address.") : lastFailure;
    }

    private SourceHttpResponse getFromAddress(URI uri, String address, long deadlineNanos) throws IOException {
        int port = uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
        try (var plainSocket = new Socket()) {
            plainSocket.connect(
                    new InetSocketAddress(address, port),
                    boundedTimeoutMillis(deadlineNanos, connectTimeout));
            Socket socket = plainSocket;
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                socket = secureSocket(plainSocket, uri.getHost(), port, deadlineNanos);
            }

            setRemainingTimeout(socket, deadlineNanos);
            writeRequest(socket, uri, port);
            var input = new DeadlineInputStream(socket.getInputStream(), socket, deadlineNanos);
            var head = readHead(input);
            byte[] body = isRedirect(head.statusCode()) ? new byte[0] : readBody(input, head.headers());
            return new SourceHttpResponse(uri, head.statusCode(), head.headers(), body);
        } catch (SocketTimeoutException exception) {
            throw new IOException("Source request timed out.", exception);
        }
    }

    private Socket secureSocket(Socket plainSocket, String host, int port, long deadlineNanos) throws IOException {
        String tlsHost = withoutIpv6Brackets(host);
        var sslSocket = (SSLSocket) sslSocketFactory.createSocket(plainSocket, tlsHost, port, true);
        SSLParameters parameters = sslSocket.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        if (!isIpLiteral(tlsHost)) {
            parameters.setServerNames(java.util.List.of(new SNIHostName(tlsHost)));
        }
        sslSocket.setSSLParameters(parameters);
        setRemainingTimeout(sslSocket, deadlineNanos);
        sslSocket.startHandshake();
        return sslSocket;
    }

    private void writeRequest(Socket socket, URI uri, int port) throws IOException {
        String target = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) {
            target += "?" + uri.getRawQuery();
        }
        boolean defaultPort = ("https".equalsIgnoreCase(uri.getScheme()) && port == 443)
                || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80);
        String authorityHost = uri.getHost().contains(":") && !uri.getHost().startsWith("[")
                ? "[" + uri.getHost() + "]"
                : uri.getHost();
        String authority = defaultPort ? authorityHost : authorityHost + ":" + port;
        String request = "GET " + target + " HTTP/1.1\r\n"
                + "Host: " + authority + "\r\n"
                + "User-Agent: GTUBlogBot/0.1 (+https://gtublog.dev)\r\n"
                + "Accept: text/html, application/xhtml+xml, application/xml, application/rss+xml, application/atom+xml;q=0.9\r\n"
                + "Accept-Encoding: identity\r\n"
                + "Connection: close\r\n\r\n";
        socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
    }

    private ResponseHead readHead(InputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        int matched = 0;
        while (bytes.size() < MAX_HEADER_BYTES) {
            int value = input.read();
            if (value < 0) {
                throw new IOException("Source response ended before its headers were complete.");
            }
            bytes.write(value);
            matched = switch (matched) {
                case 0 -> value == '\r' ? 1 : 0;
                case 1 -> value == '\n' ? 2 : (value == '\r' ? 1 : 0);
                case 2 -> value == '\r' ? 3 : 0;
                case 3 -> value == '\n' ? 4 : 0;
                default -> matched;
            };
            if (matched == 4) {
                return parseHead(bytes.toString(StandardCharsets.ISO_8859_1));
            }
        }
        throw new IOException("Source response headers exceeded the allowed size.");
    }

    private ResponseHead parseHead(String rawHead) throws IOException {
        String[] lines = rawHead.split("\\r\\n");
        if (lines.length == 0) {
            throw new IOException("Source response status line is missing.");
        }
        String[] statusParts = lines[0].split(" ", 3);
        if (statusParts.length < 2 || !statusParts[0].startsWith("HTTP/")) {
            throw new IOException("Source response status line is invalid.");
        }
        int statusCode;
        try {
            statusCode = Integer.parseInt(statusParts[1]);
        } catch (NumberFormatException exception) {
            throw new IOException("Source response status code is invalid.", exception);
        }

        var headers = new LinkedHashMap<String, String>();
        for (int index = 1; index < lines.length; index++) {
            int separator = lines[index].indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String name = lines[index].substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = lines[index].substring(separator + 1).trim();
            headers.merge(name, value, (first, second) -> first + "," + second);
        }
        return new ResponseHead(statusCode, Map.copyOf(headers));
    }

    private byte[] readBody(InputStream input, Map<String, String> headers) throws IOException {
        String transferEncoding = headers.getOrDefault("transfer-encoding", "").toLowerCase(Locale.ROOT);
        if (transferEncoding.contains("chunked")) {
            return readChunkedBody(input);
        }

        String contentLengthHeader = headers.get("content-length");
        if (contentLengthHeader != null) {
            long contentLength;
            try {
                contentLength = Long.parseLong(contentLengthHeader);
            } catch (NumberFormatException exception) {
                throw new IOException("Source response content length is invalid.", exception);
            }
            if (contentLength < 0 || contentLength > MAX_RESPONSE_BYTES) {
                throw new IOException("Source response exceeded the allowed size.");
            }
            return readExactly(input, (int) contentLength);
        }
        return readUntilEnd(input, MAX_RESPONSE_BYTES);
    }

    private byte[] readChunkedBody(InputStream input) throws IOException {
        var body = new ByteArrayOutputStream();
        while (true) {
            String line = readAsciiLine(input);
            String sizeToken = line.split(";", 2)[0].trim();
            int size;
            try {
                size = Integer.parseUnsignedInt(sizeToken, 16);
            } catch (NumberFormatException exception) {
                throw new IOException("Source response chunk size is invalid.", exception);
            }
            if (size == 0) {
                while (!readAsciiLine(input).isEmpty()) {
                    // Consume bounded trailer lines.
                }
                return body.toByteArray();
            }
            if (size > MAX_RESPONSE_BYTES - body.size()) {
                throw new IOException("Source response exceeded the allowed size.");
            }
            body.writeBytes(readExactly(input, size));
            if (input.read() != '\r' || input.read() != '\n') {
                throw new IOException("Source response chunk delimiter is invalid.");
            }
        }
    }

    private String readAsciiLine(InputStream input) throws IOException {
        var line = new ByteArrayOutputStream();
        while (line.size() < MAX_LINE_BYTES) {
            int value = input.read();
            if (value < 0) {
                throw new IOException("Source response ended unexpectedly.");
            }
            if (value == '\r') {
                if (input.read() != '\n') {
                    throw new IOException("Source response line delimiter is invalid.");
                }
                return line.toString(StandardCharsets.US_ASCII);
            }
            line.write(value);
        }
        throw new IOException("Source response line exceeded the allowed size.");
    }

    private byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Source response ended unexpectedly.");
        }
        return bytes;
    }

    private byte[] readUntilEnd(InputStream input, int limit) throws IOException {
        var output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (true) {
            int read = input.read(buffer);
            if (read < 0) {
                return output.toByteArray();
            }
            if (read > limit - output.size()) {
                throw new IOException("Source response exceeded the allowed size.");
            }
            output.write(buffer, 0, read);
        }
    }

    private int boundedTimeoutMillis(long deadlineNanos, Duration upperBound) throws SocketTimeoutException {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new SocketTimeoutException("Source request deadline elapsed.");
        }
        long timeoutMillis = Math.min(upperBound.toMillis(), Math.max(1, Duration.ofNanos(remainingNanos).toMillis()));
        return Math.toIntExact(timeoutMillis);
    }

    private void setRemainingTimeout(Socket socket, long deadlineNanos) throws IOException {
        socket.setSoTimeout(boundedTimeoutMillis(deadlineNanos, requestTimeout));
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private String withoutIpv6Brackets(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.chars().allMatch(character -> Character.isDigit(character) || character == '.');
    }

    public record SourceHttpResponse(URI uri, int statusCode, Map<String, String> headers, byte[] body) {
        public String firstHeader(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    record RequestDeadline(long deadlineNanos) {
    }

    private record ResponseHead(int statusCode, Map<String, String> headers) {
    }

    private final class DeadlineInputStream extends InputStream {
        private final InputStream delegate;
        private final Socket socket;
        private final long deadlineNanos;

        private DeadlineInputStream(InputStream delegate, Socket socket, long deadlineNanos) {
            this.delegate = delegate;
            this.socket = socket;
            this.deadlineNanos = deadlineNanos;
        }

        @Override
        public int read() throws IOException {
            setRemainingTimeout(socket, deadlineNanos);
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            setRemainingTimeout(socket, deadlineNanos);
            return delegate.read(bytes, offset, length);
        }
    }
}
