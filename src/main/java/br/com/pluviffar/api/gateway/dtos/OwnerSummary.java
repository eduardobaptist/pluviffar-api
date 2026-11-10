package br.com.pluviffar.api.gateway.dtos;

import br.com.pluviffar.api.user.entities.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Produtor dono do equipamento (nunca exposto na área pública).")
public record OwnerSummary(
        @Schema(description = "ID do produtor.", example = "019a6f3e-8b1c-7d2e-9f40-1a2b3c4d5e6f",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Nome do produtor.", example = "Maria da Silva", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Email do produtor.", example = "maria@exemplo.com.br", requiredMode = Schema.RequiredMode.REQUIRED)
        String email
) {
    public static OwnerSummary from(User user) {
        return new OwnerSummary(user.getId(), user.getName(), user.getEmail());
    }
}
