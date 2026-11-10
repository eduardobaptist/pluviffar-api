package br.com.pluviffar.api.auth;

import br.com.pluviffar.api.support.ApiClient;
import br.com.pluviffar.api.support.ApiClient.Response;
import br.com.pluviffar.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFlowTest extends IntegrationTest {

    @Test
    void csrfEndpointIssuesCookieWithSameValueAsBody() {
        ApiClient client = newClient();
        Response csrf = client.get("/api/auth/csrf");

        assertThat(csrf.status()).isEqualTo(200);
        assertThat(csrf.json().path("headerName").asString()).isEqualTo("X-XSRF-TOKEN");
        assertThat(csrf.json().path("token").asString()).isNotBlank().isEqualTo(client.cookie("XSRF-TOKEN"));
        assertThat(client.cookie("SESSION")).as("anônimo não cria sessão").isNull();
    }

    @Test
    void loginWithoutCsrfHeaderIsRejected() {
        ApiClient client = newClient().withoutCsrfHeader();
        Response login = client.login(ADMIN_EMAIL, "qualquer-coisa");

        assertThat(login.status()).isEqualTo(403);
        assertThat(login.errorCode()).isEqualTo("CSRF_TOKEN_INVALID");
    }

    @Test
    void invalidCredentialsUseSingleMessageAndCarryTraceId() {
        Response wrongPassword = newClient().login(ADMIN_EMAIL, "senha-errada-123");
        Response unknownEmail = newClient().login("ninguem@test.local", "senha-errada-123");

        for (Response r : new Response[]{wrongPassword, unknownEmail}) {
            assertThat(r.status()).isEqualTo(401);
            assertThat(r.errorCode()).isEqualTo("INVALID_CREDENTIALS");
            assertThat(r.json().path("message").asString()).isEqualTo("Email ou senha inválidos.");
            assertThat(r.json().path("traceId").asString())
                    .isNotBlank().isEqualTo(r.headers().firstValue("X-Trace-Id").orElseThrow());
        }
    }

    @Test
    void protectedEndpointWithoutSessionReturns401Json() {
        Response me = newClient().get("/api/auth/me");

        assertThat(me.status()).isEqualTo(401);
        assertThat(me.errorCode()).isEqualTo("UNAUTHENTICATED");
        assertThat(me.headers().firstValue("Location")).as("sem redirect").isEmpty();
    }

    @Test
    void loginRotatesCsrfTokenAndCreatesHttpOnlySession() {
        ApiClient admin = adminClient(); // garante a senha do ADMIN já trocada
        ApiClient client = newClient();
        client.get("/api/auth/csrf");
        String tokenBefore = client.cookie("XSRF-TOKEN");

        Response login = client.post("/api/auth/login", Map.of("email", "ADMIN@test.local", "password", ADMIN_PASSWORD));

        assertThat(login.status()).isEqualTo(200);
        assertThat(login.json().path("email").asString()).isEqualTo(ADMIN_EMAIL);
        assertThat(login.json().path("role").asString()).isEqualTo("ADMIN");
        assertThat(client.cookie("SESSION")).isNotBlank();
        assertThat(client.cookie("XSRF-TOKEN")).isNotBlank().isNotEqualTo(tokenBefore);
        assertThat(login.headers().allValues("Set-Cookie"))
                .anySatisfy(c -> assertThat(c).startsWith("SESSION=").containsIgnoringCase("HttpOnly")
                        .containsIgnoringCase("SameSite=Lax"));
        assertThat(admin.get("/api/auth/me").status()).isEqualTo(200);
    }

    @Test
    void mustChangePasswordBlocksApiUntilPasswordIsChanged() {
        ApiClient admin = adminClient();
        String email = uniqueEmail("novo-admin");
        createUser(admin, email, "ADMIN", "provisoria-123");

        ApiClient client = newClient();
        Response login = client.login(email, "provisoria-123");
        assertThat(login.json().path("mustChangePassword").asBoolean()).isTrue();

        Response blocked = client.get("/api/users");
        assertThat(blocked.status()).isEqualTo(403);
        assertThat(blocked.errorCode()).isEqualTo("PASSWORD_CHANGE_REQUIRED");
        assertThat(client.get("/api/auth/me").status()).as("/api/auth/** continua liberado").isEqualTo(200);

        Response wrongCurrent = client.put("/api/auth/password",
                Map.of("currentPassword", "errada-123", "newPassword", "nova-senha-123"));
        assertThat(wrongCurrent.status()).isEqualTo(400);
        assertThat(wrongCurrent.errorCode()).isEqualTo("CURRENT_PASSWORD_INCORRECT");

        Response same = client.put("/api/auth/password",
                Map.of("currentPassword", "provisoria-123", "newPassword", "provisoria-123"));
        assertThat(same.status()).isEqualTo(400);
        assertThat(same.errorCode()).isEqualTo("NEW_PASSWORD_SAME_AS_CURRENT");

        Response changed = client.put("/api/auth/password",
                Map.of("currentPassword", "provisoria-123", "newPassword", "nova-senha-123"));
        assertThat(changed.status()).isEqualTo(204);
        assertThat(client.get("/api/users").status()).isEqualTo(200);
        assertThat(client.get("/api/auth/me").json().path("mustChangePassword").asBoolean()).isFalse();
    }

    @Test
    void changingOwnPasswordRevokesOtherSessionsOnly() {
        ApiClient admin = adminClient();
        String email = uniqueEmail("produtor");
        createUser(admin, email, "PRODUCER", "provisoria-123");
        ApiClient first = loginReady(email, "provisoria-123", "segunda-senha-123");
        ApiClient second = newClient();
        assertThat(second.login(email, "segunda-senha-123").status()).isEqualTo(200);

        assertThat(first.put("/api/auth/password",
                Map.of("currentPassword", "segunda-senha-123", "newPassword", "terceira-senha-123")).status())
                .isEqualTo(204);

        assertThat(first.get("/api/auth/me").status()).isEqualTo(200);
        assertThat(second.get("/api/auth/me").status()).isEqualTo(401);
    }

    @Test
    void logoutInvalidatesSessionAndIsIdempotent() {
        ApiClient admin = adminClient();

        Response logout = admin.post("/api/auth/logout", null);
        assertThat(logout.status()).isEqualTo(204);
        assertThat(admin.get("/api/auth/me").status()).isEqualTo(401);

        ApiClient anonymous = newClient();
        anonymous.get("/api/auth/csrf");
        assertThat(anonymous.post("/api/auth/logout", null).status()).isEqualTo(204);
    }

    @Test
    void deactivatedUserCannotLogIn() {
        ApiClient admin = adminClient();
        String email = uniqueEmail("inativo");
        String id = createUser(admin, email, "PRODUCER", "provisoria-123");
        assertThat(admin.patch("/api/users/" + id + "/status", Map.of("active", false)).status()).isEqualTo(200);

        Response login = newClient().login(email, "provisoria-123");
        assertThat(login.status()).isEqualTo(401);
        assertThat(login.errorCode()).as("não revela que a conta existe").isEqualTo("INVALID_CREDENTIALS");
    }
}
