package com.vibecircle.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.json.JsonParserFactory;

/** Tiny HTTP client for tests: real requests against the running application, no mocks. */
public final class Api {

    private final String base;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public Api(String base) {
        this.base = base;
    }

    public record Resp(int status, String body) {

        public Map<String, Object> json() {
            return JsonParserFactory.getJsonParser().parseMap(body);
        }
                public List<Object> jsonList() {
            return JsonParserFactory.getJsonParser().parseList(body);
        }

        /** value("items", 0, "id"): String keys walk objects, Integer keys walk arrays. */
        public Object value(Object... path) {
            Object cur = json();
            for (Object key : path) {
                if (cur == null) {
                    return null;
                }
                if (key instanceof Integer i) {
                    cur = ((List<?>) cur).get(i);
                } else {
                    cur = ((Map<?, ?>) cur).get(key);
                }
            }
            return cur;
        }

        public long longValue(Object... path) {
            return ((Number) value(path)).longValue();
        }

        public String text(Object... path) {
            Object v = value(path);
            return v == null ? null : v.toString();
        }

        public List<Long> ids(String listField) {
            return ids(listField, "id");
        }

        public List<Long> ids(String listField, String key) {
            return ((List<?>) value(listField)).stream()
                    .map(item -> ((Number) ((Map<?, ?>) item).get(key)).longValue()).toList();
        }

        public List<String> strings(String listField, String key) {
            return ((List<?>) value(listField)).stream()
                    .map(item -> String.valueOf(((Map<?, ?>) item).get(key))).toList();
        }
    }

    // ------------------------------------------------------------- requests
    public Resp get(String path, String token) {
        return send("GET", path, token, null, null);
    }

    public Resp post(String path, String token, String json) {
        return send("POST", path, token, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    public Resp postEmpty(String path, String token) {
        return send("POST", path, token, null, null);
    }

    public Resp put(String path, String token, String json) {
        return send("PUT", path, token, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    public Resp patch(String path, String token, String json) {
        return send("PATCH", path, token, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    public Resp delete(String path, String token) {
        return send("DELETE", path, token, null, null);
    }

    public Resp multipart(String path, String token, Multipart parts) {
        return send("POST", path, token, parts.contentType(), parts.build());
    }

    private Resp send(String method, String path, String token, String contentType, byte[] body) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Resp(r.statusCode(), r.body());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // ----------------------------------------------------------- json helper
    /** json("a", "x", "n", 5, "ok", true) -> {"a":"x","n":5,"ok":true} */
    public static String json(Object... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quote(String.valueOf(keyValues[i]))).append(':');
            Object v = keyValues[i + 1];
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append(quote(v.toString()));
            }
        }
        return sb.append('}').toString();
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    // ------------------------------------------------------------- multipart
    public static final class Multipart {
        private final String boundary = "----it" + UUID.randomUUID();
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        public Multipart field(String name, String value) {
            write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n");
            return this;
        }

        public Multipart file(String name, String filename, String contentType, byte[] data) {
            write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\""
                    + filename + "\"\r\nContent-Type: " + contentType + "\r\n\r\n");
            out.writeBytes(data);
            write("\r\n");
            return this;
        }

        public String contentType() {
            return "multipart/form-data; boundary=" + boundary;
        }

        public byte[] build() {
            ByteArrayOutputStream copy = new ByteArrayOutputStream();
            copy.writeBytes(out.toByteArray());
            copy.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return copy.toByteArray();
        }

        private void write(String s) {
            out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}
