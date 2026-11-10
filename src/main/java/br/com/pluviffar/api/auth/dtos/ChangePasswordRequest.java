package br.com.pluviffar.api.auth.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Troca da própria senha. Encerra as outras sessões do usuário e libera o acesso quando a troca era obrigatória.")
public record ChangePasswordRequest(
        @Schema(description = "Senha atual.", example = "senhaProvisoria123", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{auth.current-password.required}")
        String currentPassword,

        @Schema(description = "Nova senha (8 a 72 caracteres, diferente da atual).", example = "minhaNovaSenha456",
                requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 72)
        @NotBlank(message = "{user.password.required}")
        @Size(min = 8, max = 72, message = "{user.password.size}")
        String newPassword
) {
}
