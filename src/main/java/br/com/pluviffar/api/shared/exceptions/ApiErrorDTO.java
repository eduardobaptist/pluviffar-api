package br.com.pluviffar.api.shared.exceptions;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ApiError", description = "Corpo padrão de toda resposta de erro (4xx/5xx).")
public record ApiErrorDTO(
        @Schema(description = "Código estável do erro (enum ErrorCode); use-o no frontend para decidir o comportamento.",
                example = "VALIDATION_ERROR", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @Schema(description = "Mensagem pronta para exibir ao usuário (pt-BR).",
                example = "Há campos inválidos na requisição.", requiredMode = Schema.RequiredMode.REQUIRED)
        String message,

        @Schema(description = "Erros por campo (apenas em VALIDATION_ERROR; vazio nos demais).",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<FieldErrorDTO> errors,

        @Schema(description = "Identificador da requisição (também no header X-Trace-Id); informe-o ao reportar problemas.",
                example = "6f1c2a9e4b7d4e0f9a3b1c2d3e4f5a6b", requiredMode = Schema.RequiredMode.REQUIRED)
        String traceId
) {
}
