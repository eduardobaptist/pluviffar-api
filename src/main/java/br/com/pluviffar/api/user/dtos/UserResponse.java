package br.com.pluviffar.api.user.dtos;

import br.com.pluviffar.api.user.entities.Role;
import br.com.pluviffar.api.user.entities.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Dados de um usuário (nunca inclui a senha).")
public record UserResponse(
        @Schema(description = "Identificador do usuário.", example = "019a6f3e-8b1c-7d2e-9f40-1a2b3c4d5e6f",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Nome.", example = "Maria da Silva", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Email (sempre em minúsculas).", example = "maria@exemplo.com.br",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(description = "Perfil.", example = "PRODUCER", requiredMode = Schema.RequiredMode.REQUIRED)
        Role role,
        @Schema(description = "Se o usuário pode fazer login.", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean active,
        @Schema(description = "Se precisa trocar a senha antes de usar a plataforma.", example = "false",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean mustChangePassword,
        @Schema(description = "Criação (ISO-8601, UTC).", example = "2026-11-10T12:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(description = "Última alteração (ISO-8601, UTC).", example = "2026-11-10T12:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive(),
                user.isMustChangePassword(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
