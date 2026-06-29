package com.gtublog.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SourceUrlPolicyTests {

    @Test
    void acceptsPublicHttpAndHttpsDestinations() throws Exception {
        var policy = policyResolvingTo("93.184.216.34");

        assertThat(policy.validateFetchUrl("https://example.com/articles/1").toString())
                .isEqualTo("https://example.com/articles/1");
        assertThat(policy.validateFetchUrl("http://example.com/feed.xml").toString())
                .isEqualTo("http://example.com/feed.xml");
    }

    @Test
    void rejectsNonHttpCredentialsAndLocalHostnames() {
        var policy = policyResolvingToUnchecked("93.184.216.34");

        assertThatThrownBy(() -> policy.validateStoredUrl("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validateStoredUrl("https://user:password@example.com/private"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validateStoredUrl("http://localhost:8080/admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPrivateMetadataReservedAndMixedDnsAnswers() throws Exception {
        assertRejected("127.0.0.1");
        assertRejected("10.0.0.1");
        assertRejected("100.64.0.1");
        assertRejected("169.254.169.254");
        assertRejected("192.168.1.10");
        assertRejected("198.18.0.1");
        assertRejected("203.0.113.10");
        assertRejected("fc00::1");
        assertRejected("2001:db8::1");
        assertRejected("64:ff9b::a9fe:a9fe");
        assertRejected("64:ff9b:1::a9fe:a9fe");
        assertRejected("2002:7f00:1::");
        assertRejected("2001:0000:4136:e378:8000:63bf:3fff:fdd2");
        assertRejected("::ffff:127.0.0.1");
        assertRejected("100::1");
        assertRejected("2001:2::1");
        assertRejected("3fff::1");

        var mixedPolicy = new SourceUrlPolicy(Set.of(), host -> new InetAddress[] {
                InetAddress.getByName("93.184.216.34"),
                InetAddress.getByName("127.0.0.1")
        });
        assertThatThrownBy(() -> mixedPolicy.validateFetchUrl("https://example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private network");
    }

    @Test
    void privateAddressOverrideIsExplicitAndStillEnforcesHttpStructure() {
        var policy = new SourceUrlPolicy(Set.of("127.0.0.1"), host -> new InetAddress[] {InetAddress.getLoopbackAddress()});

        assertThat(policy.validateFetchUrl("http://127.0.0.1:8080/feed").getHost()).isEqualTo("127.0.0.1");
        assertThatThrownBy(() -> policy.validateFetchUrl("file:///tmp/feed"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertRejected(String address) throws Exception {
        var policy = policyResolvingTo(address);
        assertThatThrownBy(() -> policy.validateFetchUrl("https://source.example/feed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private network");
    }

    private SourceUrlPolicy policyResolvingTo(String address) throws Exception {
        var resolved = InetAddress.getByName(address);
        return new SourceUrlPolicy(Set.of(), host -> new InetAddress[] {resolved});
    }

    private SourceUrlPolicy policyResolvingToUnchecked(String address) {
        try {
            return policyResolvingTo(address);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
