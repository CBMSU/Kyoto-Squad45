package br.com.kyoto.whatsapp;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookTest {
    private static final Config CONFIG = new Config(8080, "verify", "secret", "meta-token", "123", "v25.0",
        URI.create("https://ai.example/chat"), "ai-token", URI.create("https://db.example"), "service-key");
    private static final String PAYLOAD = """
        {"object":"whatsapp_business_account","entry":[{"changes":[{"field":"messages","value":{
          "metadata":{"phone_number_id":"123"},"messages":[{"id":"wamid.1","from":"5582999999999",
          "type":"text","text":{"body":"Olá"}}]}}]}]}
        """;

    static final class Fixture implements Webhook.Inbox {
        final Map<String, String> states = new ConcurrentHashMap<>();
        final AtomicInteger aiCalls = new AtomicInteger();
        final AtomicInteger sends = new AtomicInteger();
        boolean aiFails;
        boolean sendFails;
        String reply = "Olá, sou a SophIA.";
        String sentReply;
        final Webhook webhook = new Webhook(CONFIG, this, message -> {
            aiCalls.incrementAndGet();
            if (aiFails) throw new Exception("AI unavailable");
            return reply;
        }, (message, text) -> {
            assert message.from().equals("5582999999999");
            sends.incrementAndGet();
            sentReply = text;
            if (sendFails) throw new Exception("Send timeout");
        });

        @Override public synchronized boolean claim(String id) {
            if (states.containsKey(id) && !states.get(id).equals("failed")) return false;
            states.put(id, "processing");
            return true;
        }
        @Override public void status(String id, String status) { states.put(id, status); }
        Webhook.Result receive(String payload) throws Exception {
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            return webhook.receive(body, sign(body));
        }
    }

    static String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    public static void main(String[] args) throws Exception {
        boolean assertions = false;
        assert assertions = true;
        if (!assertions) throw new IllegalStateException("Use java -ea");
        Fixture verification = new Fixture();
        assert verification.webhook.verify(Map.of("hub.mode", "subscribe", "hub.verify_token", "verify",
            "hub.challenge", "123")).body().equals("123");
        assert verification.webhook.verify(Map.of("hub.mode", "subscribe", "hub.verify_token", "wrong",
            "hub.challenge", "123")).status() == 403;
        System.out.println("PASS: Meta verification");

        byte[] body = PAYLOAD.getBytes(StandardCharsets.UTF_8);
        assert verification.webhook.receive(body, "invalid").status() == 401;
        assert verification.webhook.receive((PAYLOAD + " ").getBytes(StandardCharsets.UTF_8), sign(body)).status() == 401;
        assert verification.aiCalls.get() == 0;
        assert verification.receive("{invalid").status() == 400;
        assert verification.webhook.receive(new byte[1024 * 1024 + 1], "invalid").status() == 413;
        System.out.println("PASS: signature, tampering, malformed JSON and size limit");

        Fixture flow = new Fixture();
        assert flow.receive(PAYLOAD).status() == 200;
        assert flow.receive(PAYLOAD).status() == 200;
        assert flow.aiCalls.get() == 1 && flow.sends.get() == 1;
        assert flow.states.get("wamid.1").equals("sent");
        assert flow.sentReply.equals("Olá, sou a SophIA.");
        System.out.println("PASS: AI response and duplicate delivery");

        Fixture concurrent = new Fixture();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> concurrent.receive(PAYLOAD));
            var second = executor.submit(() -> concurrent.receive(PAYLOAD));
            assert first.get().status() == 200 && second.get().status() == 200;
            assert concurrent.aiCalls.get() == 1 && concurrent.sends.get() == 1;
        } finally { executor.shutdown(); }
        System.out.println("PASS: concurrent duplicate delivery");

        Fixture ignored = new Fixture();
        assert ignored.receive(PAYLOAD.replace("\"type\":\"text\"", "\"type\":\"audio\"")).status() == 200;
        assert ignored.receive(PAYLOAD.replace("\"phone_number_id\":\"123\"", "\"phone_number_id\":\"456\"")).status() == 200;
        assert ignored.receive("{\"object\":\"whatsapp_business_account\",\"entry\":[]}").status() == 200;
        assert ignored.aiCalls.get() == 0;
        System.out.println("PASS: media, status and another number are ignored");

        Fixture failure = new Fixture();
        failure.aiFails = true;
        assert failure.receive(PAYLOAD).status() == 503;
        assert failure.states.get("wamid.1").equals("failed") && failure.sends.get() == 0;
        failure.aiFails = false;
        assert failure.receive(PAYLOAD).status() == 200;
        assert failure.aiCalls.get() == 2 && failure.sends.get() == 1;
        System.out.println("PASS: AI failure permits retry");

        Fixture batch = new Fixture();
        String second = "{\"id\":\"wamid.2\",\"from\":\"5582999999999\",\"type\":\"text\",\"text\":{\"body\":\"Segunda\"}}";
        String batchPayload = PAYLOAD.replace("\"body\":\"Olá\"}}]", "\"body\":\"Olá\"}}," + second + "]");
        assert Webhook.messages(Json.parse(batchPayload), "123").size() == 2;
        batch.aiFails = true;
        assert batch.receive(batchPayload).status() == 503;
        batch.aiFails = false;
        assert batch.receive(batchPayload).status() == 200;
        assert batch.states.get("wamid.1").equals("sent") && batch.states.get("wamid.2").equals("sent");
        assert batch.receive(batchPayload).status() == 200 && batch.sends.get() == 2;
        System.out.println("PASS: batch retry processes both messages without duplicate sends");

        AtomicInteger unexpectedAi = new AtomicInteger();
        Webhook.Inbox unavailable = new Webhook.Inbox() {
            @Override public boolean claim(String id) throws Exception { throw new Exception("Database unavailable"); }
            @Override public void status(String id, String status) { throw new AssertionError("No message was claimed"); }
        };
        Webhook unavailableDb = new Webhook(CONFIG, unavailable,
            message -> { unexpectedAi.incrementAndGet(); return "reply"; },
            (message, reply) -> { throw new AssertionError("Must not send without persistence"); });
        assert unavailableDb.receive(body, sign(body)).status() == 503;
        assert unexpectedAi.get() == 0;
        System.out.println("PASS: database failure stops processing before calling AI");

        Fixture timeout = new Fixture();
        timeout.sendFails = true;
        assert timeout.receive(PAYLOAD).status() == 503;
        assert timeout.states.get("wamid.1").equals("uncertain");
        assert timeout.receive(PAYLOAD).status() == 200;
        assert timeout.sends.get() == 1;
        System.out.println("PASS: uncertain sends do not retry automatically");

        Fixture empty = new Fixture();
        empty.reply = " ";
        assert empty.receive(PAYLOAD).status() == 503 && empty.sends.get() == 0;
        Fixture longReply = new Fixture();
        longReply.reply = "😀".repeat(4200);
        assert longReply.receive(PAYLOAD).status() == 200;
        assert longReply.sentReply.codePointCount(0, longReply.sentReply.length()) == 4096;
        System.out.println("PASS: empty reply and Unicode length limit");

        Object json = Json.parse("{\"text\":\"Olá\\n\\\"😀\\\"\",\"items\":[true,null,-1.5e2]}");
        assert Json.parse(Json.write(json)).equals(json);
        for (String invalid : new String[]{"01", "[1,]", "{\"a\":1,\"a\":2}", "true false", "\"\\x\""}) {
            try { Json.parse(invalid); throw new AssertionError("Accepted invalid JSON"); }
            catch (IllegalArgumentException expected) { /* rejected */ }
        }
        assert Application.query("hub.challenge=ol%C3%A1%2B").get("hub.challenge").equals("olá+");
        try { Application.query("hub.mode=%XX"); throw new AssertionError("Accepted invalid query"); }
        catch (IllegalArgumentException expected) { /* rejected */ }
        System.out.println("PASS: JSON round trip, malformed input and query encoding");
        System.out.println("All Java checks passed.");
    }
}
