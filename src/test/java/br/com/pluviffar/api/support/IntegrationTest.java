package br.com.pluviffar.api.support;

import br.com.pluviffar.api.support.ApiClient.Response;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base dos testes de integração: app real em porta aleatória + TimescaleDB em container.
 * O contexto (e o container) é compartilhado entre as classes de teste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.placeholders.admin_email=" + IntegrationTest.ADMIN_EMAIL,
        "spring.flyway.placeholders.admin_password=" + IntegrationTest.ADMIN_INITIAL_PASSWORD,
        "pluviffar.security.secure-cookies=false" // o cliente de teste usa http
})
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    // credencial descartável do banco efêmero de teste (não é usada em nenhum ambiente real)
    public static final String ADMIN_EMAIL = "admin@test.local";
    public static final String ADMIN_INITIAL_PASSWORD = "initial-admin-password";
    public static final String ADMIN_PASSWORD = "changed-admin-password";

    /** A seed obriga o ADMIN a trocar a senha; o primeiro adminClient() faz a troca e as chamadas seguintes reusam. */
    private static String currentAdminPassword = ADMIN_INITIAL_PASSWORD;

    @LocalServerPort
    protected int port;

    protected ApiClient newClient() {
        return new ApiClient("http://localhost:" + port);
    }

    /** Cliente logado como o ADMIN da seed, já com a senha trocada. */
    protected synchronized ApiClient adminClient() {
        ApiClient admin = newClient();
        Response login = admin.login(ADMIN_EMAIL, currentAdminPassword);
        assertThat(login.status()).isEqualTo(200);
        if (login.json().path("mustChangePassword").asBoolean()) {
            Response change = admin.put("/api/auth/password",
                    Map.of("currentPassword", currentAdminPassword, "newPassword", ADMIN_PASSWORD));
            assertThat(change.status()).isEqualTo(204);
            currentAdminPassword = ADMIN_PASSWORD;
        }
        return admin;
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    /** ADMIN cadastra um usuário (senha provisória) e devolve o id. */
    protected String createUser(ApiClient admin, String email, String role, String password) {
        Response created = admin.post("/api/users",
                Map.of("name", "Usuário Teste", "email", email, "role", role, "password", password));
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        return created.json().path("id").asString();
    }

    /** Login de um usuário novo + troca da senha provisória (fica liberado para usar a API). */
    protected ApiClient loginReady(String email, String temporaryPassword, String newPassword) {
        ApiClient client = newClient();
        assertThat(client.login(email, temporaryPassword).status()).isEqualTo(200);
        assertThat(client.put("/api/auth/password",
                Map.of("currentPassword", temporaryPassword, "newPassword", newPassword)).status()).isEqualTo(204);
        return client;
    }
}
