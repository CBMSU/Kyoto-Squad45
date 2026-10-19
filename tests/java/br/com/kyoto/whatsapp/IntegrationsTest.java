package br.com.kyoto.whatsapp;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IntegrationsTest {
    private static final Config CONFIG = new Config(8080, "verify", "secret", "meta-token", "123", "v25.0",
        URI.create("https://ai.example/chat"), "ai-token", URI.create("https://db.example/"), "service-key");
    private static final Webhook.Message MESSAGE = new Webhook.Message("wamid.a+b/==", "5582999999999", "Olá \"SophIA\"\n😀");

    record Call(URI uri, String method, Map<String, String> headers, Object body) {}
    interface Checked { void run() throws Exception; }

    static void fails(Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Expected request to fail"); }
        catch (IOException | IllegalArgumentException expected) { /* rejected */ }
    }

    public static void main(String[] args) throws Exception {
        boolean assertions = false;
        assert assertions = true;
        if (!assertions) throw new IllegalStateException("Use java -ea");
        List<Call> calls = new ArrayList<>();
        Integrations integrations = new Integrations(CONFIG, (uri, method, headers, body) -> {
            calls.add(new Call(uri, method, headers, Json.parse(body)));
            if (uri.getPath().contains("/rpc/")) return new Integrations.Reply(200, "true");
            if (method.equals("PATCH")) return new Integrations.Reply(204, "");
            if (uri.getHost().equals("ai.example")) return new Integrations.Reply(200, "{\"reply\":\"Resposta 😀\"}");
            return new Integrations.Reply(200, "{\"messages\":[{\"id\":\"wamid.sent\"}]}");
        });
        assert integrations.claim(MESSAGE.id());
        assert calls.get(0).uri().toString().equals("https://db.example/rest/v1/rpc/claim_whatsapp_message");
        assert Json.get(calls.get(0).body(), "p_message_id").equals(MESSAGE.id());
        assert calls.get(0).headers().get("apikey").equals("service-key");
        assert calls.get(0).headers().get("Authorization").equals("Bearer service-key");
        assert integrations.reply(MESSAGE).equals("Resposta 😀");
        Call ai = calls.get(1);
        assert ai.uri().equals(CONFIG.sophiaUrl()) && ai.method().equals("POST");
        assert ai.headers().get("Authorization").equals("Bearer ai-token");
        assert !ai.headers().containsKey("apikey");
        assert Json.get(ai.body(), "message").equals(MESSAGE.body());
        assert Json.get(ai.body(), "conversationId").equals("whatsapp:123:5582999999999");
        assert Json.get(ai.body(), "messageId").equals(MESSAGE.id());
        integrations.send(MESSAGE, "Resposta 😀");
        Call meta = calls.get(2);
        assert meta.uri().toString().equals("https://graph.facebook.com/v25.0/123/messages");
        assert meta.headers().get("Authorization").equals("Bearer meta-token");
        assert !meta.headers().containsKey("apikey");
        assert Json.get(meta.body(), "messaging_product").equals("whatsapp");
        assert Json.get(meta.body(), "to").equals(MESSAGE.from());
        assert Json.get(Json.get(meta.body(), "context"), "message_id").equals(MESSAGE.id());
        assert Json.get(Json.get(meta.body(), "text"), "body").equals("Resposta 😀");
        integrations.status(MESSAGE.id(), "sent");
        Call patch = calls.get(3);
        assert patch.method().equals("PATCH");
        assert patch.uri().getRawQuery().equals("message_id=eq.wamid.a%2Bb%2F%3D%3D");
        assert Json.get(patch.body(), "status").equals("sent");
        System.out.println("PASS: database, AI and Meta URLs, headers and JSON contracts");

        for (String body : List.of("null", "{}", "\"true\"", "[]", "invalid")) {
            Integrations malformed = new Integrations(CONFIG, (u, m, h, b) -> new Integrations.Reply(200, body));
            fails(() -> malformed.claim(MESSAGE.id()));
            fails(() -> malformed.send(MESSAGE, "reply"));
        }
        Integrations duplicate = new Integrations(CONFIG, (u, m, h, b) -> new Integrations.Reply(200, "false"));
        assert !duplicate.claim(MESSAGE.id());
        for (int status : new int[]{301, 400, 401, 429, 500, 503}) {
            Integrations error = new Integrations(CONFIG, (u, m, h, b) -> new Integrations.Reply(status, "upstream details"));
            fails(() -> error.claim(MESSAGE.id()));
            fails(() -> error.reply(MESSAGE));
            fails(() -> error.send(MESSAGE, "reply"));
        }
        System.out.println("PASS: malformed success bodies, redirects and upstream failures are rejected");

        Map<String, String> env = new HashMap<>(Map.of(
            "WHATSAPP_VERIFY_TOKEN", "verify", "WHATSAPP_APP_SECRET", "secret",
            "WHATSAPP_ACCESS_TOKEN", "meta-token", "WHATSAPP_PHONE_NUMBER_ID", "123",
            "WHATSAPP_GRAPH_VERSION", "v25.0", "SOPHIA_API_URL", "https://ai.example/chat",
            "SOPHIA_API_TOKEN", "ai-token", "SUPABASE_URL", "https://db.example/",
            "SUPABASE_SERVICE_ROLE_KEY", "service-key"));
        assert Config.from(env).port() == 8080;
        for (String url : List.of("http://db.example", "https://db.example?x=y", "https://db.example/rest/v1", "https://user:pass@db.example")) {
            env.put("SUPABASE_URL", url);
            fails(() -> Config.from(env));
        }
        env.put("SUPABASE_URL", "https://db.example");
        for (String key : List.copyOf(env.keySet())) {
            String saved = env.remove(key);
            fails(() -> Config.from(env));
            env.put(key, saved);
        }
        System.out.println("PASS: missing configuration and invalid database origins fail at startup");
        System.out.println("All integration contract checks passed.");
    }
}
