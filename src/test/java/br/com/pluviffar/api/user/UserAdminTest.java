package br.com.pluviffar.api.user;

import br.com.pluviffar.api.support.ApiClient;
import br.com.pluviffar.api.support.ApiClient.Response;
import br.com.pluviffar.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserAdminTest extends IntegrationTest {

    @Test
    void anonymousAndProducerCannotManageUsers() {
        Response anonymous = newClient().get("/api/users");
        assertThat(anonymous.status()).isEqualTo(401);
        assertThat(anonymous.errorCode()).isEqualTo("UNAUTHENTICATED");

        ApiClient admin = adminClient();
        String email = uniqueEmail("produtor");
        createUser(admin, email, "PRODUCER", "provisoria-123");
        ApiClient producer = loginReady(email, "provisoria-123", "nova-senha-123");

        Response denied = producer.get("/api/users");
        assertThat(denied.status()).isEqualTo(403);
        assertThat(denied.errorCode()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void createValidatesFieldsWithPortugueseMessages() {
        Response invalid = adminClient().post("/api/users",
                Map.of("name", "", "email", "nao-e-email", "password", "123"));

        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(invalid.json().path("message").asString()).isEqualTo("Há campos inválidos na requisição.");
        assertThat(invalid.json().path("errors").valueStream()
                .map(e -> e.path("field").asString() + ": " + e.path("message").asString()))
                .contains("name: Informe o nome.",
                        "email: Informe um email válido.",
                        "role: Informe o perfil.",
                        "password: A senha deve ter entre 8 e 72 caracteres.");
    }

    @Test
    void createNormalizesEmailAndRejectsDuplicateIgnoringCase() {
        ApiClient admin = adminClient();
        String email = uniqueEmail("Maria");

        Response created = admin.post("/api/users",
                Map.of("name", "  Maria  ", "email", "  " + email.toUpperCase() + " ", "role", "PRODUCER",
                        "password", "provisoria-123"));
        assertThat(created.status()).isEqualTo(201);
        JsonNode body = created.json();
        assertThat(created.headers().firstValue("Location")).hasValueSatisfying(
                l -> assertThat(l).endsWith("/api/users/" + body.path("id").asString()));
        assertThat(body.path("email").asString()).isEqualTo(email.toLowerCase());
        assertThat(body.path("name").asString()).isEqualTo("Maria");
        assertThat(body.path("mustChangePassword").asBoolean()).isTrue();
        assertThat(body.has("passwordHash")).isFalse();

        Response duplicate = admin.post("/api/users",
                Map.of("name", "Outra", "email", email.toUpperCase(), "role", "ADMIN", "password", "provisoria-123"));
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.errorCode()).isEqualTo("EMAIL_ALREADY_IN_USE");
    }

    @Test
    void listSupportsSearchPaginationAndWhitelistedSort() {
        ApiClient admin = adminClient();
        String marker = "busca" + UUID.randomUUID().toString().substring(0, 6);
        createUser(admin, uniqueEmail(marker), "PRODUCER", "provisoria-123");
        createUser(admin, uniqueEmail(marker), "PRODUCER", "provisoria-123");
        createUser(admin, uniqueEmail(marker), "ADMIN", "provisoria-123");

        Response page = admin.get("/api/users?search=" + marker + "&role=PRODUCER&size=1&sort=email,desc");
        assertThat(page.status()).isEqualTo(200);
        assertThat(page.json().path("totalElements").asLong()).isEqualTo(2);
        assertThat(page.json().path("totalPages").asInt()).isEqualTo(2);
        assertThat(page.json().path("content").size()).isEqualTo(1);

        Response badSort = admin.get("/api/users?sort=passwordHash,asc");
        assertThat(badSort.status()).isEqualTo(400);
        assertThat(badSort.errorCode()).isEqualTo("INVALID_PARAMETER");
    }

    @Test
    void getReturns404ForUnknownAnd400ForMalformedId() {
        ApiClient admin = adminClient();

        Response unknown = admin.get("/api/users/" + UUID.randomUUID());
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.errorCode()).isEqualTo("USER_NOT_FOUND");
        assertThat(unknown.json().path("message").asString()).isEqualTo("Usuário não encontrado.");

        Response malformed = admin.get("/api/users/nao-e-uuid");
        assertThat(malformed.status()).isEqualTo(400);
        assertThat(malformed.errorCode()).isEqualTo("INVALID_PARAMETER");
    }

    @Test
    void changingRoleRevokesSessionsAndAdminCannotChangeOwnRole() {
        ApiClient admin = adminClient();
        String email = uniqueEmail("promovido");
        String id = createUser(admin, email, "PRODUCER", "provisoria-123");
        ApiClient user = loginReady(email, "provisoria-123", "nova-senha-123");

        Response updated = admin.put("/api/users/" + id, Map.of("name", "Promovido", "email", email, "role", "ADMIN"));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.json().path("role").asString()).isEqualTo("ADMIN");
        assertThat(user.get("/api/auth/me").status()).as("sessão revogada").isEqualTo(401);

        String adminId = admin.get("/api/auth/me").json().path("id").asString();
        Response ownRole = admin.put("/api/users/" + adminId,
                Map.of("name", "Administrador", "email", ADMIN_EMAIL, "role", "PRODUCER"));
        assertThat(ownRole.status()).isEqualTo(409);
        assertThat(ownRole.errorCode()).isEqualTo("CANNOT_CHANGE_OWN_ROLE");

        Response emailTaken = admin.put("/api/users/" + id, Map.of("name", "X", "email", ADMIN_EMAIL, "role", "ADMIN"));
        assertThat(emailTaken.status()).isEqualTo(409);
        assertThat(emailTaken.errorCode()).isEqualTo("EMAIL_ALREADY_IN_USE");
    }

    @Test
    void deactivatingRevokesSessionsImmediatelyAndAdminCannotDeactivateSelf() {
        ApiClient admin = adminClient();
        String email = uniqueEmail("desativado");
        String id = createUser(admin, email, "PRODUCER", "provisoria-123");
        ApiClient user = loginReady(email, "provisoria-123", "nova-senha-123");
        assertThat(user.get("/api/auth/me").status()).isEqualTo(200);

        Response deactivated = admin.patch("/api/users/" + id + "/status", Map.of("active", false));
        assertThat(deactivated.status()).isEqualTo(200);
        assertThat(deactivated.json().path("active").asBoolean()).isFalse();
        assertThat(user.get("/api/auth/me").status()).isEqualTo(401);

        String adminId = admin.get("/api/auth/me").json().path("id").asString();
        Response self = admin.patch("/api/users/" + adminId + "/status", Map.of("active", false));
        assertThat(self.status()).isEqualTo(409);
        assertThat(self.errorCode()).isEqualTo("CANNOT_DEACTIVATE_SELF");
    }

    @Test
    void resetPasswordRevokesSessionsAndForcesChange() {
        ApiClient admin = adminClient();
        String email = uniqueEmail("reset");
        String id = createUser(admin, email, "PRODUCER", "provisoria-123");
        ApiClient user = loginReady(email, "provisoria-123", "nova-senha-123");

        assertThat(admin.put("/api/users/" + id + "/password", Map.of("newPassword", "redefinida-123")).status())
                .isEqualTo(204);

        assertThat(user.get("/api/auth/me").status()).isEqualTo(401);
        Response login = newClient().login(email, "redefinida-123");
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.json().path("mustChangePassword").asBoolean()).isTrue();
    }
}
