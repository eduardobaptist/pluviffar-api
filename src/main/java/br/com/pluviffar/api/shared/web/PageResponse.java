package br.com.pluviffar.api.shared.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Envelope padrão de paginação (não expõe o PageImpl do Spring Data, cujo JSON não é estável). */
@Schema(description = "Página de resultados. Parâmetros: page (0-based), size (padrão 20, máx. 100), sort=campo,asc|desc.")
public record PageResponse<T>(
        @Schema(description = "Itens da página.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<T> content,
        @Schema(description = "Número da página (começa em 0).", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(description = "Tamanho da página.", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
        @Schema(description = "Total de itens.", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,
        @Schema(description = "Total de páginas.", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages
) {
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
