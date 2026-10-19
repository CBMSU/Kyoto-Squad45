package br.com.kyoto.whatsapp;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class HttpTest {
    public static void main(String[] args) throws Exception {
        boolean assertions = false;
        assert assertions = true;
        if (!assertions) throw new IllegalStateException("Use java -ea");
        var fixture = new WebhookTest.Fixture();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
        server.createContext("/webhooks/whatsapp", Application.handler(fixture.webhook));
        server.start();
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/webhooks/whatsapp");
            var verified = client.send(HttpRequest.newBuilder(URI.create(base +
                "?hub.mode=subscribe&hub.verify_token=verify&hub.challenge=987")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assert verified.statusCode() == 200 && verified.body().equals("987");
            assert verified.headers().firstValue("Content-Type").orElse("").startsWith("text/plain");
            assert client.send(HttpRequest.newBuilder(URI.create(base +
                "?hub.mode=subscribe&hub.verify_token=wrong&hub.challenge=987")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode() == 403;
        } finally { server.stop(0); }
        System.out.println("PASS: HTTP verification and response content type");
        checkRequests(fixture);
        System.out.println("All HTTP checks passed.");
    }

    private static void checkRequests(WebhookTest.Fixture fixture) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
        server.createContext("/webhooks/whatsapp", Application.handler(fixture.webhook));
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/webhooks/whatsapp");
            var method = client.send(HttpRequest.newBuilder(base).PUT(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assert method.statusCode() == 405 && method.headers().firstValue("Allow").orElse("").equals("GET, POST");
            assert client.send(HttpRequest.newBuilder(URI.create(base + "/extra")).build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 404;
            assert client.send(HttpRequest.newBuilder(URI.create(base + "?hub.mode=subscribe&hub.mode=subscribe")).build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 400;
            byte[] body = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
            assert client.send(HttpRequest.newBuilder(base).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 401;
            assert client.send(HttpRequest.newBuilder(base).header("X-Hub-Signature-256", WebhookTest.sign(body))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
            byte[] invalid = new byte[]{(byte) 0xc3, (byte) 0x28};
            assert client.send(HttpRequest.newBuilder(base).header("X-Hub-Signature-256", WebhookTest.sign(invalid))
                .POST(HttpRequest.BodyPublishers.ofByteArray(invalid)).build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 400;
            byte[] oversized = new byte[1024 * 1024 + 1];
            assert client.send(HttpRequest.newBuilder(base).POST(HttpRequest.BodyPublishers.ofByteArray(oversized)).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode() == 413;
            assert fixture.aiCalls.get() == 0;
        } finally { server.stop(0); }
        System.out.println("PASS: HTTP routes, methods, duplicate params, signature, UTF-8 and body limit");
    }
}
