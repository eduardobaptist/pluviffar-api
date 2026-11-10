package br.com.pluviffar.api.shared.config;

import br.com.pluviffar.api.auth.config.SecurityConfig;
import br.com.pluviffar.api.shared.exceptions.ApiErrorDTO;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Contrato OpenAPI (docs/openapi.yaml é gerado a partir daqui pelo OpenApiSpecExportTest).
 * Segurança e respostas de erro genéricas são aplicadas por regra (espelhando o SecurityConfig), para que
 * todo endpoint as declare sem repetir anotações; respostas específicas ficam nos controllers.
 */
@Configuration
public class OpenApiConfig {

    static final String SESSION_SCHEME = "sessionCookie";
    static final String CSRF_SCHEME = "csrfHeader";
    private static final String ERROR_REF = "#/components/schemas/ApiError";

    private static final String DESCRIPTION = """
            API de gerenciamento do PluvIFFar (monitoramento pluviométrico).

            ## Autenticação (sessão por cookie, sem JWT)
            1. `GET /api/auth/csrf` → recebe o cookie `XSRF-TOKEN` (legível por JS) e o token no corpo.
            2. `POST /api/auth/login` com o header `X-XSRF-TOKEN` → cria a sessão (cookie `SESSION`, HttpOnly).
               O ID da sessão e o token CSRF são renovados: leia de novo o cookie `XSRF-TOKEN`.
            3. `GET /api/auth/me` → usuário e perfil. 401 = sem sessão ou expirada (volte ao login).
               Se `mustChangePassword = true`, só `/api/auth/**` funciona até `PUT /api/auth/password`.
            4. `POST /api/auth/logout` → encerra a sessão.

            Toda requisição POST/PUT/PATCH/DELETE precisa do header `X-XSRF-TOKEN` com o valor atual do cookie
            `XSRF-TOKEN` (reemitido em toda resposta). No navegador, envie os cookies (`credentials: 'include'`).
            A sessão expira após 30 min de inatividade.

            ## Erros
            Todo erro 4xx/5xx tem o corpo `ApiError`: `code` (estável, use no front), `message` (pt-BR, pronta para exibir),
            `errors` (por campo, em `VALIDATION_ERROR`) e `traceId` (também no header `X-Trace-Id`).
            Recurso de outro produtor responde 404 (não revela existência).

            ## Paginação e período
            Listas usam `page` (0-based), `size` (padrão 20, máx. 100) e `sort=campo,asc|desc`, e devolvem `PageResponse`.
            Filtros de período usam `from`/`to` em ISO-8601 (ex.: `2026-11-10T00:00:00Z`).
            """;

    @Bean
    public OpenAPI pluviffarOpenApi() {
        return new OpenAPI()
                .info(new Info().title("PluvIFFar API").version("v1").description(DESCRIPTION))
                .components(new Components()
                        .addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("SESSION")
                                .description("Cookie de sessão (HttpOnly), criado no login."))
                        .addSecuritySchemes(CSRF_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name(SecurityConfig.CSRF_HEADER)
                                .description("Token CSRF: valor do cookie XSRF-TOKEN. Obrigatório em POST/PUT/PATCH/DELETE.")));
    }

    @Bean
    public OpenApiCustomizer securityAndErrorsCustomizer() {
        return openApi -> {
            Map<String, Schema> schemas = ModelConverters.getInstance().readAll(ApiErrorDTO.class);
            schemas.forEach(openApi.getComponents()::addSchemas);

            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                Access access = accessOf(path, method);
                operation.setSecurity(access.requirements(method));
                addDefaultErrors(operation, access, method);
            }));
        };
    }

    private enum Access {
        PUBLIC, CSRF_ONLY, SESSION;

        List<SecurityRequirement> requirements(PathItem.HttpMethod method) {
            boolean unsafe = isUnsafe(method);
            return switch (this) {
                case PUBLIC -> List.of();
                case CSRF_ONLY -> List.of(new SecurityRequirement().addList(CSRF_SCHEME));
                case SESSION -> unsafe
                        ? List.of(new SecurityRequirement().addList(SESSION_SCHEME).addList(CSRF_SCHEME))
                        : List.of(new SecurityRequirement().addList(SESSION_SCHEME));
            };
        }
    }

    // espelha as regras de authorizeHttpRequests do SecurityConfig
    private static Access accessOf(String path, PathItem.HttpMethod method) {
        if (path.startsWith("/api/public/") || path.equals("/api/auth/csrf")) {
            return Access.PUBLIC;
        }
        if (path.equals("/api/auth/login") || path.equals("/api/auth/logout")) {
            return Access.CSRF_ONLY;
        }
        return Access.SESSION;
    }

    private static boolean isUnsafe(PathItem.HttpMethod method) {
        return Set.of(PathItem.HttpMethod.POST, PathItem.HttpMethod.PUT, PathItem.HttpMethod.PATCH,
                PathItem.HttpMethod.DELETE).contains(method);
    }

    private static void addDefaultErrors(Operation operation, Access access, PathItem.HttpMethod method) {
        ApiResponses responses = operation.getResponses() != null ? operation.getResponses() : new ApiResponses();
        boolean hasInput = operation.getRequestBody() != null
                || (operation.getParameters() != null && !operation.getParameters().isEmpty());
        if (hasInput) {
            putIfAbsent(responses, "400", "VALIDATION_ERROR (com errors por campo), MALFORMED_REQUEST ou INVALID_PARAMETER.");
        }
        if (access == Access.SESSION) {
            putIfAbsent(responses, "401", "UNAUTHENTICATED: sem sessão ou sessão expirada.");
            putIfAbsent(responses, "403", isUnsafe(method)
                    ? "ACCESS_DENIED, CSRF_TOKEN_INVALID ou PASSWORD_CHANGE_REQUIRED."
                    : "ACCESS_DENIED ou PASSWORD_CHANGE_REQUIRED.");
        } else if (access == Access.CSRF_ONLY) {
            putIfAbsent(responses, "403", "CSRF_TOKEN_INVALID: header X-XSRF-TOKEN ausente ou diferente do cookie.");
        }
        putIfAbsent(responses, "500", "INTERNAL_ERROR.");
        operation.setResponses(responses);
    }

    private static void putIfAbsent(ApiResponses responses, String status, String description) {
        if (!responses.containsKey(status)) {
            responses.addApiResponse(status, new ApiResponse().description(description)
                    .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                            new MediaType().schema(new Schema<>().$ref(ERROR_REF)))));
        }
    }
}
