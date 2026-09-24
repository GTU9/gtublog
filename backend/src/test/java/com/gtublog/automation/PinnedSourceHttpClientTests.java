package com.gtublog.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PinnedSourceHttpClientTests {

    private static final InetAddress IPV4_LOOPBACK = loopbackAddress();

    @Test
    void enforcesOneDeadlineAcrossAnEntireRedirectChain() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2);
                var firstServer = new ServerSocket(0, 50, IPV4_LOOPBACK);
                var secondServer = new ServerSocket(0, 50, IPV4_LOOPBACK)) {
            executor.submit(() -> {
                try (var socket = firstServer.accept()) {
                    consumeRequest(socket);
                    Thread.sleep(120);
                    socket.getOutputStream().write(("HTTP/1.1 302 Found\r\nLocation: http://second.invalid:"
                                    + secondServer.getLocalPort()
                                    + "/final\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    Thread.sleep(100);
                }
                return null;
            });
            executor.submit(() -> {
                try (var socket = secondServer.accept()) {
                    consumeRequest(socket);
                    Thread.sleep(120);
                    byte[] body = "too late".getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: " + body.length
                                    + "\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(body);
                    socket.getOutputStream().flush();
                }
                return null;
            });

            var resolver = (SourceUrlPolicy.AddressResolver) host -> new InetAddress[] {IPV4_LOOPBACK};
            var policy = new SourceUrlPolicy(Set.of("first.invalid", "second.invalid"), resolver);
            var client = new PinnedSourceHttpClient(Duration.ofMillis(100), Duration.ofMillis(180));
            var service = new SourceCollectionService(null, null, policy, client, new ObjectMapper());

            assertThatThrownBy(() -> service.fetch("http://first.invalid:" + firstServer.getLocalPort() + "/start"))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("timed out");
        }
    }

    @Test
    void rejectsTlsCertificateThatDoesNotMatchTheOriginalHostname() throws Exception {
        var server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort().dynamicHttpsPort());
        server.start();
        try {
            server.stubFor(get(urlEqualTo("/secure"))
                    .willReturn(aResponse().withStatus(200).withBody("secure response")));
            var client = new PinnedSourceHttpClient(
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(2),
                    trustTestCertificateSocketFactory());

            var target = new SourceUrlPolicy.ResolvedSourceUrl(
                    URI.create("https://localhost:" + server.httpsPort() + "/secure"),
                    List.of(InetAddress.getLoopbackAddress()));
            assertThatThrownBy(() -> client.get(target))
                    .isInstanceOf(javax.net.ssl.SSLHandshakeException.class)
                    .hasMessageContaining("No name matching localhost found");
        } finally {
            server.stop();
        }
    }

    @Test
    void connectsToTheValidatedAddressWithoutResolvingTheRequestHostnameAgain() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor();
                var server = new ServerSocket(0, 50, IPV4_LOOPBACK)) {
            var hostHeader = executor.submit(() -> {
                try (var socket = server.accept();
                        var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))) {
                    String host = null;
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        if (line.regionMatches(true, 0, "Host:", 0, 5)) {
                            host = line.substring(5).trim();
                        }
                    }
                    byte[] body = "pinned response".getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: " + body.length
                                    + "\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(body);
                    socket.getOutputStream().flush();
                    Thread.sleep(100);
                    return host;
                }
            });

            var target = new SourceUrlPolicy.ResolvedSourceUrl(
                    URI.create("http://dns-rebind.invalid:" + server.getLocalPort() + "/feed"),
                    List.of(IPV4_LOOPBACK));
            var response = new PinnedSourceHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(2)).get(target);

            assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("pinned response");
            assertThat(hostHeader.get()).isEqualTo("dns-rebind.invalid:" + server.getLocalPort());
        }
    }

    private void consumeRequest(java.net.Socket socket) throws Exception {
        var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        while (!reader.readLine().isEmpty()) {
            // Consume the request.
        }
    }

    @Test
    void enforcesOneDeadlineAcrossHeadersAndTheEntireBody() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor();
                var server = new ServerSocket(0, 50, IPV4_LOOPBACK)) {
            executor.submit(() -> {
                try (var socket = server.accept()) {
                    var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    while (!reader.readLine().isEmpty()) {
                        // Consume the request.
                    }
                    socket.getOutputStream().write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 1\r\nConnection: close\r\n\r\n"
                                    .getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    Thread.sleep(500);
                }
                return null;
            });

            var target = new SourceUrlPolicy.ResolvedSourceUrl(
                    URI.create("http://slow.invalid:" + server.getLocalPort() + "/feed"),
                    List.of(IPV4_LOOPBACK));

            assertThatThrownBy(() -> new PinnedSourceHttpClient(Duration.ofMillis(100), Duration.ofMillis(150)).get(target))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("timed out");
        }
    }

    @Test
    void rejectsAnOversizedBodyBeforeReadingIt() throws Exception {
        var neverRead = new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("Body must not be read after an oversized Content-Length.");
            }
        };
        var client = new PinnedSourceHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(2));
        assertThatThrownBy(() -> client.readBody(neverRead, Map.of("content-length", "2097153")))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("allowed size");
    }

    @Test
    void decodesChunkedBodiesAndTrailers() throws Exception {
        var response = "4\r\nWiki\r\n5\r\npedia\r\n0\r\nX-Trace: test\r\n\r\n";
        var client = new PinnedSourceHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(2));

        assertThat(client.readBody(
                        new ByteArrayInputStream(response.getBytes(StandardCharsets.US_ASCII)),
                        Map.of("transfer-encoding", "chunked")))
                .isEqualTo("Wikipedia".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void rejectsOversizedAndMalformedChunkedBodies() {
        var client = new PinnedSourceHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(2));
        var headers = Map.of("transfer-encoding", "chunked");

        assertThatThrownBy(() -> client.readBody(
                        new ByteArrayInputStream("200001\r\n".getBytes(StandardCharsets.US_ASCII)), headers))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("allowed size");
        assertThatThrownBy(() -> client.readBody(
                        new ByteArrayInputStream("not-hex\r\n".getBytes(StandardCharsets.US_ASCII)), headers))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("chunk size is invalid");
        assertThatThrownBy(() -> client.readBody(
                        new ByteArrayInputStream("4\r\nWikiXX0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)),
                        headers))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("chunk delimiter is invalid");
    }

    private javax.net.ssl.SSLSocketFactory trustTestCertificateSocketFactory() throws Exception {
        var trustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[0];
            }
        };
        var context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] {trustManager}, null);
        return context.getSocketFactory();
    }

    private static InetAddress loopbackAddress() {
        try {
            return InetAddress.getByName("127.0.0.1");
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
