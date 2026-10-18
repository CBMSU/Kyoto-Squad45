package br.com.kyoto.whatsapp;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class Webhook {
    record Message(String id, String from, String body) {}
    record Result(int status, String body) {}

    interface Inbox {
        boolean claim(String messageId) throws Exception;
        void status(String messageId, String status) throws Exception;
    }
    interface Ai { String reply(Message message) throws Exception; }
    interface Sender { void send(Message message, String reply) throws Exception; }

    private final Config config;
    private final Inbox inbox;
    private final Ai ai;
    private final Sender sender;

    Webhook(Config config, Inbox inbox, Ai ai, Sender sender) {
        this.config = config;
        this.inbox = inbox;
        this.ai = ai;
        this.sender = sender;
    }

    Result verify(Map<String, String> params) {
        String challenge = params.get("hub.challenge");
        if (!"subscribe".equals(params.get("hub.mode")) || challenge == null || challenge.isBlank() ||
            !config.verifyToken().equals(params.get("hub.verify_token"))) return new Result(403, "Forbidden");
        return new Result(200, challenge);
    }

    Result receive(byte[] body, String signature) {
        if (body.length > 1024 * 1024) return new Result(413, "Payload too large");
        try {
            if (!validSignature(body, signature, config.appSecret())) return new Result(401, "Invalid signature");
            Object payload;
            try {
                String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body)).toString();
                payload = Json.parse(text);
            } catch (IllegalArgumentException | CharacterCodingException e) {
                return new Result(400, "Invalid JSON");
            }
            for (Message message : messages(payload, config.phoneId())) {
                if (!inbox.claim(message.id())) continue;
                boolean sending = false;
                try {
                    String reply = ai.reply(message);
                    if (reply == null || reply.isBlank()) throw new IllegalStateException("Empty AI reply");
                    reply = reply.strip();
                    reply = reply.substring(0, reply.offsetByCodePoints(0, Math.min(4096, reply.codePointCount(0, reply.length()))));
                    sending = true;
                    sender.send(message, reply);
                    inbox.status(message.id(), "sent");
                } catch (Exception e) {
                    // The send may have succeeded despite a timeout. Avoid automatic double sends.
                    inbox.status(message.id(), sending ? "uncertain" : "failed");
                    throw e;
                }
            }
            return new Result(200, "Received");
        } catch (Exception e) {
            // Do not log tokens, phone numbers, prompts or upstream response bodies.
            System.err.println("WhatsApp webhook processing failed");
            return new Result(503, "Processing failed");
        }
    }

    static boolean validSignature(byte[] body, String signature, String secret) throws Exception {
        if (signature == null || !signature.matches("sha256=[a-f0-9]{64}")) return false;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return MessageDigest.isEqual(mac.doFinal(body), HexFormat.of().parseHex(signature.substring(7)));
    }

    static List<Message> messages(Object payload, String phoneId) {
        List<Message> result = new ArrayList<>();
        if (!"whatsapp_business_account".equals(Json.get(payload, "object"))) return result;
        for (Object entry : Json.list(Json.get(payload, "entry"))) {
            for (Object change : Json.list(Json.get(entry, "changes"))) {
                Object value = Json.get(change, "value");
                if (!"messages".equals(Json.get(change, "field")) ||
                    !phoneId.equals(Json.get(Json.get(value, "metadata"), "phone_number_id"))) continue;
                for (Object item : Json.list(Json.get(value, "messages"))) {
                    String id = Json.text(Json.get(item, "id"));
                    String from = Json.text(Json.get(item, "from"));
                    String text = Json.text(Json.get(Json.get(item, "text"), "body"));
                    if ("text".equals(Json.get(item, "type")) && !id.isBlank() &&
                        from.matches("[0-9]{5,20}") && !text.isBlank()) result.add(new Message(id, from, text));
                }
            }
        }
        return result;
    }
}
