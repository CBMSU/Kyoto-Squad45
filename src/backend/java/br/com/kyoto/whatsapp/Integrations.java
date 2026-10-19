package br.com.kyoto.whatsapp;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

final class Integrations implements Webhook.Inbox, Webhook.Ai, Webhook.Sender {
    record Reply(int status, String body) {}
    interface Transport {
        Reply send(URI uri, String method, Map<String, String> headers, String body) throws Exception;
    }
    private final Config config;
    private final Transport transport;

    Integrations(Config config) {
        this(config, httpTransport());
    }

    Integrations(Config config, Transport transport) {
        this.config = config;
        this.transport = transport;
    }

    private static Transport httpTransport() {
        // Do not follow redirects carrying credentials to another origin.
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();
        return (uri, method, headers, body) -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .method(method, HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body());
        };
    }

    private String request(URI uri, String method, String token, String apiKey, Object body) throws Exception {
        Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Content-Type", "application/json");
        if (apiKey != null) headers.put("apikey", apiKey);
        Reply response = transport.send(uri, method, Map.copyOf(headers), Json.write(body));
        if (response.status() < 200 || response.status() >= 300) {
            throw new IOException("Upstream request failed");
        }
        return response.body();
    }

    private String database(String path, String method, Object body) throws Exception {
        String base = config.supabaseUrl().toString().replaceAll("/+$", "");
        return request(URI.create(base + "/rest/v1/" + path), method,
            config.serviceKey(), config.serviceKey(), body);
    }

    @Override public boolean claim(String messageId) throws Exception {
        Object result = Json.parse(database("rpc/claim_whatsapp_message", "POST", Map.of("p_message_id", messageId)));
        if (!(result instanceof Boolean claimed)) throw new IOException("Invalid database claim response");
        return claimed;
    }

    @Override public void status(String messageId, String status) throws Exception {
        String encoded = URLEncoder.encode(messageId, StandardCharsets.UTF_8).replace("+", "%20");
        database("whatsapp_inbox?message_id=eq." + encoded, "PATCH",
            Map.of("status", status, "updated_at", Instant.now().toString()));
    }

    @Override public String reply(Webhook.Message message) throws Exception {
        Object response = Json.parse(request(config.sophiaUrl(), "POST", config.sophiaToken(), null,
            Map.of("message", message.body(), "conversationId", "whatsapp:" + config.phoneId() + ":" + message.from(),
                "channel", "whatsapp", "phone", message.from(), "messageId", message.id())));
        return Json.text(Json.get(response, "reply"));
    }

    @Override public void send(Webhook.Message message, String reply) throws Exception {
        URI endpoint = URI.create("https://graph.facebook.com/" + config.graphVersion() + "/" + config.phoneId() + "/messages");
        Object response = Json.parse(request(endpoint, "POST", config.accessToken(), null, Map.of(
            "messaging_product", "whatsapp", "recipient_type", "individual", "to", message.from(),
            "type", "text", "context", Map.of("message_id", message.id()),
            "text", Map.of("body", reply, "preview_url", false))));
        var messages = Json.list(Json.get(response, "messages"));
        if (messages.isEmpty() || Json.text(Json.get(messages.get(0), "id")).isBlank()) {
            throw new IOException("Meta did not acknowledge the outgoing message");
        }
    }
}
