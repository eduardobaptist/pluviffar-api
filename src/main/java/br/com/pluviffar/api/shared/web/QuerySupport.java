package br.com.pluviffar.api.shared.web;

import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import org.springframework.data.domain.Pageable;

import java.util.Locale;
import java.util.Set;

/** Utilitários de listagem: busca textual (LIKE seguro) e whitelist de ordenação. */
public final class QuerySupport {

    public static final char LIKE_ESCAPE = '\\';

    private QuerySupport() {
    }

    /** Padrão "%termo%" em minúsculas, com %, _ e \ escapados; null se a busca estiver vazia. */
    public static String containsPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** Recusa sort por campos fora da lista (evita ordenar por colunas internas/sensíveis). */
    public static void requireSortable(Pageable pageable, Set<String> allowed) {
        pageable.getSort().forEach(order -> {
            if (!allowed.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER);
            }
        });
    }
}
