package br.com.pluviffar.api.user.dtos;

import br.com.pluviffar.api.user.entities.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Cadastro de usuário pelo ADMIN. A senha é provisória: o usuário deve trocá-la no primeiro login.")
public record CreateUserRequest(
        @Schema(description = "Nome.", example = "Maria da Silva", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 120)
        @NotBlank(message = "{user.name.required}")
        @Size(max = 120, message = "{user.name.size}")
        String name,

        @Schema(description = "Email (único, sem diferenciar maiúsculas).", example = "maria@exemplo.com.br",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 254)
        @NotBlank(message = "{user.email.required}")
        @Email(message = "{user.email.invalid}")
        @Size(max = 254, message = "{user.email.size}")
        String email,

        @Schema(description = "Perfil.", example = "PRODUCER", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{user.role.required}")
        Role role,

        @Schema(description = "Senha provisória (8 a 72 caracteres).", example = "senhaProvisoria123",
                requiredMode = Schema.RequiredMode.REQUIRED, minLength = 8, maxLength = 72)
        @NotBlank(message = "{user.password.required}")
        @Size(min = 8, max = 72, message = "{user.password.size}")
        String password
) {
    // espaços nas pontas são removidos antes da validação
    public CreateUserRequest {
        name = trim(name);
        email = trim(email);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
