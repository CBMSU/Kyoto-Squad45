package br.com.kyoto.whatsapp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON codec so the service can run with only a JDK. */
final class Json {
    private final String source;
    private int position;

    private Json(String source) { this.source = source; }

    static Object parse(String source) {
        Json parser = new Json(source);
        Object result = parser.value(0);
        parser.space();
        if (parser.position != source.length()) throw invalid();
        return result;
    }

    static Object get(Object object, String key) {
        return object instanceof Map<?, ?> map ? map.get(key) : null;
    }

    static List<?> list(Object object) {
        return object instanceof List<?> list ? list : List.of();
    }

    static String text(Object object) { return object instanceof String s ? s : ""; }

    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return quote(s);
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((key, item) -> entries.add(quote((String) key) + ":" + write(item)));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof List<?> list) {
            return "[" + String.join(",", list.stream().map(Json::write).toList()) + "]";
        }
        throw new IllegalArgumentException("Unsupported JSON value");
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    private Object value(int depth) {
        if (depth > 64) throw invalid();
        space();
        if (position >= source.length()) throw invalid();
        char c = source.charAt(position);
        if (c == '"') return string();
        if (c == '{') {
            position++;
            Map<String, Object> map = new LinkedHashMap<>();
            if (take('}')) return map;
            do {
                space();
                String key = string();
                if (!take(':') || map.containsKey(key)) throw invalid();
                map.put(key, value(depth + 1));
            } while (take(','));
            if (!take('}')) throw invalid();
            return map;
        }
        if (c == '[') {
            position++;
            List<Object> list = new ArrayList<>();
            if (take(']')) return list;
            do { list.add(value(depth + 1)); } while (take(','));
            if (!take(']')) throw invalid();
            return list;
        }
        for (String literal : List.of("true", "false", "null")) {
            if (source.startsWith(literal, position)) {
                position += literal.length();
                return literal.equals("null") ? null : Boolean.valueOf(literal);
            }
        }
        int start = position;
        while (position < source.length() && "-+0123456789.eE".indexOf(source.charAt(position)) >= 0) position++;
        String number = source.substring(start, position);
        if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw invalid();
        return new BigDecimal(number);
    }

    private String string() {
        if (position >= source.length() || source.charAt(position++) != '"') throw invalid();
        StringBuilder out = new StringBuilder();
        while (position < source.length()) {
            char c = source.charAt(position++);
            if (c == '"') return out.toString();
            if (c < 32) throw invalid();
            if (c != '\\') { out.append(c); continue; }
            if (position >= source.length()) throw invalid();
            char escape = source.charAt(position++);
            switch (escape) {
                case '"', '\\', '/' -> out.append(escape);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (position + 4 > source.length()) throw invalid();
                    String hex = source.substring(position, position + 4);
                    if (!hex.matches("[a-fA-F0-9]{4}")) throw invalid();
                    out.append((char) Integer.parseInt(hex, 16));
                    position += 4;
                }
                default -> throw invalid();
            }
        }
        throw invalid();
    }

    private boolean take(char c) {
        space();
        if (position < source.length() && source.charAt(position) == c) { position++; return true; }
        return false;
    }

    private void space() {
        while (position < source.length() && " \t\n\r".indexOf(source.charAt(position)) >= 0) position++;
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid JSON"); }
}
