package br.com.kyoto.whatsapp;

import java.net.URI;
import java.util.Map;

record Config(int port, String verifyToken, String appSecret, String accessToken,
              String phoneId, String graphVersion, URI sophiaUrl, String sophiaToken,
              URI supabaseUrl, String serviceKey) {
    static Config from(Map<String, String> env) {
        String phone = required(env, "WHATSAPP_PHONE_NUMBER_ID");
        String version = required(env, "WHATSAPP_GRAPH_VERSION");
        if (!phone.matches("[0-9]+") || !version.matches("v[0-9]+\\.[0-9]+")) {
            throw new IllegalArgumentException("Invalid WhatsApp phone ID or Graph version");
        }
        int port = Integer.parseInt(env.getOrDefault("PORT", "8080"));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid PORT");
        URI database = https(required(env, "SUPABASE_URL"));
        if (database.getRawQuery() != null ||
            !(database.getPath().isEmpty() || database.getPath().equals("/"))) {
            throw new IllegalArgumentException("SUPABASE_URL must be the project origin without a path or query");
        }
        return new Config(port, required(env, "WHATSAPP_VERIFY_TOKEN"),
            required(env, "WHATSAPP_APP_SECRET"), required(env, "WHATSAPP_ACCESS_TOKEN"),
            phone, version, https(required(env, "SOPHIA_API_URL")), required(env, "SOPHIA_API_TOKEN"),
            database, required(env, "SUPABASE_SERVICE_ROLE_KEY"));
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing configuration: " + name);
        return value;
    }

    private static URI https(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null ||
            uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Upstream URLs must use HTTPS without embedded credentials");
        }
        return uri;
    }
}
