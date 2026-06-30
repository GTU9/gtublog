package com.gtublog.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.Test;

class PinnedSourceHttpClientTests {

    @Test
    void enforcesOneDeadlineAcrossAnEntireRedirectChain() throws Exception {
        try (var firstServer = new ServerSocket(0);
                var secondServer = new ServerSocket(0);
                var executor = Executors.newFixedThreadPool(2)) {
            executor.submit(() -> {
                try (var socket = firstServer.accept()) {
                    consumeRequest(socket);
                    Thread.sleep(120);
                    socket.getOutputStream().write(("HTTP/1.1 302 Found\r\nLocation: http://second.invalid:"
                                    + secondServer.getLocalPort()
                                    + "/final\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
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

            var resolver = (SourceUrlPolicy.AddressResolver) host -> new InetAddress[] {InetAddress.getLoopbackAddress()};
            var policy = new SourceUrlPolicy(Set.of("first.invalid", "second.invalid"), resolver);
            var client = new PinnedSourceHttpClient(Duration.ofMillis(100), Duration.ofMillis(180));
            var service = new SourceCollectionService(null, null, policy, client);

            assertThatThrownBy(() -> service.fetch("http://first.invalid:" + firstServer.getLocalPort() + "/start"))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("timed out");
        }
    }

    @Test
    void rejectsTlsCertificateThatDoesNotMatchTheOriginalHostname() throws Exception {
        var server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicHttpsPort());
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
        try (var server = new ServerSocket(0);
                var executor = Executors.newSingleThreadExecutor()) {
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
                    return host;
                }
            });

            var target = new SourceUrlPolicy.ResolvedSourceUrl(
                    URI.create("http://dns-rebind.invalid:" + server.getLocalPort() + "/feed"),
                    List.of(InetAddress.getLoopbackAddress()));
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
        try (var server = new ServerSocket(0);
                var executor = Executors.newSingleThreadExecutor()) {
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
                    List.of(InetAddress.getLoopbackAddress()));

            assertThatThrownBy(() -> new PinnedSourceHttpClient(Duration.ofMillis(100), Duration.ofMillis(150)).get(target))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("timed out");
        }
    }

    @Test
    void rejectsAnOversizedBodyBeforeReadingIt() throws Exception {
        try (var server = new ServerSocket(0);
                var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                try (var socket = server.accept()) {
                    var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    while (!reader.readLine().isEmpty()) {
                        // Consume the request.
                    }
                    socket.getOutputStream().write(
                            "HTTP/1.1 200 OK\r\nContent-Length: 2097153\r\nConnection: close\r\n\r\n"
                                    .getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                }
                return null;
            });

            var target = new SourceUrlPolicy.ResolvedSourceUrl(
                    URI.create("http://oversized.invalid:" + server.getLocalPort() + "/feed"),
                    List.of(InetAddress.getLoopbackAddress()));

            assertThatThrownBy(() -> new PinnedSourceHttpClient(Duration.ofSeconds(1), Duration.ofSeconds(2)).get(target))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("allowed size");
        }
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
}
