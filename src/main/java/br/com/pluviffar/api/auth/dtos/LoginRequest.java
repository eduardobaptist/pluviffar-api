package br.com.pluviffar.api.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciais de login.")
public record LoginRequest(
        @Schema(description = "Email do usuário.", example = "maria@exemplo.com.br", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{auth.email.required}")
        String email,

        @Schema(description = "Senha.", example = "minhaSenha123", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{auth.password.required}")
        String password
) {
    // espaços nas pontas são removidos antes da validação
    public LoginRequest {
        email = trim(email);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
