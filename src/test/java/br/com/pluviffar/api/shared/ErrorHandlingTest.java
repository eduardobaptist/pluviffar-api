package br.com.pluviffar.api.shared;

import br.com.pluviffar.api.support.ApiClient;
import br.com.pluviffar.api.support.ApiClient.Response;
import br.com.pluviffar.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorHandlingTest extends IntegrationTest {

    @Test
    void malformedJsonReturns400() {
        ApiClient client = newClient();
        client.get("/api/auth/csrf");

        Response r = client.post("/api/auth/login", "{ isto não é json");
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.errorCode()).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void unknownRouteReturns404Json() {
        Response r = adminClient().get("/api/nao-existe");
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(r.json().path("errors").isArray()).isTrue();
    }

    @Test
    void unsupportedMethodReturns405() {
        Response r = adminClient().send("DELETE", "/api/auth/me", null);
        assertThat(r.status()).isEqualTo(405);
        assertThat(r.errorCode()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void everyResponseHasTraceIdHeader() {
        Response r = newClient().get("/api/auth/csrf");
        assertThat(r.headers().firstValue("X-Trace-Id")).hasValueSatisfying(id -> assertThat(id).hasSize(32));
    }
}
