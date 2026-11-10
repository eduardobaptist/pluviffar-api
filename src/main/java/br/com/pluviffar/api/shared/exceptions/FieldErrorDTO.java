package br.com.pluviffar.api.shared.exceptions;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FieldError", description = "Erro de validação de um campo.")
public record FieldErrorDTO(
        @Schema(description = "Nome do campo (ou parâmetro) inválido.", example = "email",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String field,

        @Schema(description = "Mensagem para o usuário (pt-BR).", example = "Informe um email válido.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {
}
