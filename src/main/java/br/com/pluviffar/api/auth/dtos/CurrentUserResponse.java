package br.com.pluviffar.api.auth.dtos;

import br.com.pluviffar.api.user.entities.Role;
import br.com.pluviffar.api.user.entities.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Usuário logado.")
public record CurrentUserResponse(
        @Schema(description = "Identificador do usuário.", example = "019a6f3e-8b1c-7d2e-9f40-1a2b3c4d5e6f",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
        @Schema(description = "Nome.", example = "Maria da Silva", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "Email.", example = "maria@exemplo.com.br", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(description = "Perfil (define as áreas que o front libera).", example = "PRODUCER",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Role role,
        @Schema(description = "Se true, o front deve levar o usuário à troca de senha: o resto da API responde "
                + "403 PASSWORD_CHANGE_REQUIRED até a troca.", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean mustChangePassword
) {
    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                user.isMustChangePassword());
    }
}
