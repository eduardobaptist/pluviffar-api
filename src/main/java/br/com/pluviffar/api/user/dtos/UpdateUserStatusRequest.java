package br.com.pluviffar.api.user.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Ativa ou desativa um usuário. Desativar encerra imediatamente todas as sessões dele.")
public record UpdateUserStatusRequest(
        @Schema(description = "true = ativo; false = desativado (não consegue fazer login).", example = "false",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{user.active.required}")
        Boolean active
) {
}
