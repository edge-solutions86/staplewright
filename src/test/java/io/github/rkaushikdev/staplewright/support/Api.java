package io.github.rkaushikdev.staplewright.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.staplewright.api.Shop;
import io.github.rkaushikdev.staplewright.api.StaplewrightApplication;
import io.github.rkaushikdev.staplewright.settings.Settings;
import io.github.rkaushikdev.staplewright.settings.ShopSettings;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The service as the route tests reach it: running on the suite's test database, over real http,
 * with a client small enough that a test reads like the request it makes.
 *
 * <p>One service is started per set of settings and extra routes and kept for the whole run, so a
 * test pays for the database it fills, not for a server start. The database is the shared test
 * database, so a test fills it after {@link TestDatabase#fresh()} and then asks.
 */
public final class Api {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Map<List<Object>, Api> RUNNING = new HashMap<>();

    private final ConfigurableApplicationContext context;
    private final String base;

    private Api(ConfigurableApplicationContext context) {
        this.context = context;
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        this.base = "http://localhost:" + port;
    }

    /** The service on the default settings. */
    public static Api client() {
        return client(Settings.DEFAULTS);
    }

    /** The service on these settings, with any extra routes a test brings. */
    public static synchronized Api client(ShopSettings settings, Class<?>... extra) {
        List<Object> key = List.of(settings, List.of(extra));
        return RUNNING.computeIfAbsent(
                key,
                ignored -> new Api(StaplewrightApplication.run(
                        new Shop(TestDatabase.database(), settings), List.of(extra), "--server.port=0")));
    }

    /** The running service's own objects, for a test that needs one. */
    public ConfigurableApplicationContext context() {
        return context;
    }

    public Answer get(String path) {
        return send(HttpRequest.newBuilder(uri(path, Map.of())).GET());
    }

    /** A get with query parameters, written in the order given. */
    public Answer get(String path, Object... params) {
        return send(HttpRequest.newBuilder(uri(path, pairs(params))).GET());
    }

    public Answer post(String path, Object body) {
        return send(withJson(HttpRequest.newBuilder(uri(path, Map.of())), body, "POST"));
    }

    /** A post with no body at all. */
    public Answer post(String path) {
        return send(HttpRequest.newBuilder(uri(path, Map.of())).POST(HttpRequest.BodyPublishers.noBody()));
    }

    public Answer put(String path, Object body) {
        return send(withJson(HttpRequest.newBuilder(uri(path, Map.of())), body, "PUT"));
    }

    public Answer delete(String path) {
        return send(HttpRequest.newBuilder(uri(path, Map.of())).DELETE());
    }

    /** A request with a text body, for the routes that take a file's contents. */
    public Answer postText(String path, String body, Object... params) {
        return send(HttpRequest.newBuilder(uri(path, pairs(params)))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private static HttpRequest.Builder withJson(HttpRequest.Builder request, Object body, String method) {
        try {
            return request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        } catch (IOException trouble) {
            throw new IllegalStateException(trouble);
        }
    }

    private static Map<String, Object> pairs(Object... params) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int index = 0; index < params.length; index += 2) {
            out.put((String) params[index], params[index + 1]);
        }
        return out;
    }

    private URI uri(String path, Map<String, Object> params) {
        String query = params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(String.valueOf(entry.getValue())))
                .collect(Collectors.joining("&"));
        return URI.create(base + path + (query.isEmpty() ? "" : "?" + query));
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static Answer send(HttpRequest.Builder request) {
        try {
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Answer(response.statusCode(), response.headers().firstValue("content-type"), response.body());
        } catch (IOException trouble) {
            throw new IllegalStateException(trouble);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(stopped);
        }
    }

    /** One response: its status, its content type and its body. */
    public record Answer(int status, Optional<String> contentType, String text) {

        /** The body read as a JSON object. */
        public Map<String, Object> json() {
            try {
                return JSON.readValue(text, new TypeReference<Map<String, Object>>() {});
            } catch (IOException trouble) {
                throw new IllegalStateException("not a JSON object: " + text, trouble);
            }
        }

        /** The body read as a JSON array. */
        public List<Object> jsonList() {
            try {
                return JSON.readValue(text, new TypeReference<List<Object>>() {});
            } catch (IOException trouble) {
                throw new IllegalStateException("not a JSON array: " + text, trouble);
            }
        }
    }
}
