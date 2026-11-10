package br.com.pluviffar.api.shared;

import br.com.pluviffar.api.support.ApiClient.Response;
import br.com.pluviffar.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gera docs/openapi.yaml (o contrato que vai para o frontend). Roda em todo ./mvnw test:
 * se endpoints/DTOs mudarem, o arquivo muda junto e entra no mesmo commit.
 */
class OpenApiSpecExportTest extends IntegrationTest {

    private static final Path SPEC = Path.of("docs", "openapi.yaml");

    @Test
    void exportsOpenApiSpec() throws IOException {
        Response spec = newClient().send("GET", "/v3/api-docs.yaml", null, "*/*");

        assertThat(spec.status()).isEqualTo(200);
        assertThat(spec.body())
                .contains("/api/auth/login", "/api/users/{id}", "sessionCookie", "csrfHeader", "ApiError");

        Files.createDirectories(SPEC.getParent());
        Files.writeString(SPEC, spec.body(), StandardCharsets.UTF_8);
    }
}
