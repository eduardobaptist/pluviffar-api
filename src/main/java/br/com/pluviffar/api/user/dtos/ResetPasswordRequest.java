package br.com.pluviffar.api.user.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Redefinição de senha pelo ADMIN. A nova senha é provisória (troca obrigatória no próximo login) "
        + "e todas as sessões do usuário são encerradas.")
public record ResetPasswordRequest(
        @Schema(description = "Nova senha provisória (8 a 72 caracteres).", example = "novaProvisoria123",
                requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 72)
        @NotBlank(message = "{user.password.required}")
        @Size(min = 8, max = 72, message = "{user.password.size}")
        String newPassword
) {
}
