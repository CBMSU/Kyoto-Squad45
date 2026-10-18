package br.com.kyoto.whatsapp;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class Application {
    public static void main(String[] args) throws Exception {
        Config config = Config.from(System.getenv());
        Integrations integrations = new Integrations(config);
        Webhook webhook = new Webhook(config, integrations, integrations, integrations);
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 64);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(8, 8, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), new ThreadPoolExecutor.CallerRunsPolicy());
        server.setExecutor(executor);
        server.createContext("/webhooks/whatsapp", handler(webhook));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(5);
            executor.shutdown();
        }));
        server.start();
        System.out.println("WhatsApp Java backend listening on port " + config.port());
    }

    static HttpHandler handler(Webhook webhook) {
        return exchange -> {
            try (exchange) {
                Webhook.Result result;
                if (!"/webhooks/whatsapp".equals(exchange.getRequestURI().getPath())) {
                    result = new Webhook.Result(404, "Not found");
                } else if ("GET".equals(exchange.getRequestMethod())) {
                    try { result = webhook.verify(query(exchange.getRequestURI().getRawQuery())); }
                    catch (IllegalArgumentException e) { result = new Webhook.Result(400, "Invalid query"); }
                } else if ("POST".equals(exchange.getRequestMethod())) {
                    byte[] body = exchange.getRequestBody().readNBytes(1024 * 1024 + 1);
                    result = webhook.receive(body, exchange.getRequestHeaders().getFirst("X-Hub-Signature-256"));
                } else {
                    exchange.getResponseHeaders().set("Allow", "GET, POST");
                    result = new Webhook.Result(405, "Method not allowed");
                }
                byte[] response = result.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(result.status(), response.length);
                exchange.getResponseBody().write(response);
            }
        };
    }

    static Map<String, String> query(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw == null) return result;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8);
            if (result.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Duplicate query parameter");
        }
        return result;
    }
}
