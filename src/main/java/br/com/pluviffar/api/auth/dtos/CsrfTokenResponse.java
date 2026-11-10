package br.com.pluviffar.api.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token CSRF. O mesmo valor fica no cookie XSRF-TOKEN (legível por JavaScript), reemitido a cada resposta.")
public record CsrfTokenResponse(
        @Schema(description = "Header onde o token deve ser enviado em POST/PUT/PATCH/DELETE.", example = "X-XSRF-TOKEN",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String headerName,
        @Schema(description = "Valor do token.", example = "3f0e5a2c-1b4d-4e6f-8a9b-0c1d2e3f4a5b",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String token
) {
}
