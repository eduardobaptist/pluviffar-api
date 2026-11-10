package br.com.pluviffar.api.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;

/**
 * Cliente HTTP que se comporta como o frontend: guarda cookies (SESSION, XSRF-TOKEN) e envia
 * o header X-XSRF-TOKEN com o valor do cookie em POST/PUT/PATCH/DELETE.
 */
public class ApiClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> UNSAFE = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final String baseUrl;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;
    private boolean sendCsrfHeader = true;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    public ApiClient withoutCsrfHeader() {
        this.sendCsrfHeader = false;
        return this;
    }

    public Response get(String path) {
        return send("GET", path, null);
    }

    public Response post(String path, Object body) {
        return send("POST", path, body);
    }

    public Response put(String path, Object body) {
        return send("PUT", path, body);
    }

    public Response patch(String path, Object body) {
        return send("PATCH", path, body);
    }

    /** Fluxo do front: obtém o CSRF e faz login. */
    public Response login(String email, String password) {
        get("/api/auth/csrf");
        return post("/api/auth/login", Map.of("email", email, "password", password));
    }

    public String cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst().orElse(null);
    }

    public Response send(String method, String path, Object body) {
        return send(method, path, body, "application/json");
    }

    public Response send(String method, String path, Object body, String accept) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Accept", accept);
            if (body instanceof String raw) {
                request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(raw));
            } else if (body != null) {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            } else {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            }
            String xsrf = cookie("XSRF-TOKEN");
            if (sendCsrfHeader && UNSAFE.contains(method) && xsrf != null) {
                request.header("X-XSRF-TOKEN", xsrf);
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(), response.headers());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public record Response(int status, String body, HttpHeaders headers) {

        public JsonNode json() {
            return JSON.readTree(body);
        }

        /** Campo "code" do ApiErrorDTO. */
        public String errorCode() {
            return json().path("code").asString();
        }
    }
}
