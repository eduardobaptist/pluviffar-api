package br.com.pluviffar.api.gateway.useCases;

import br.com.pluviffar.api.auth.security.CurrentUser;
import br.com.pluviffar.api.gateway.dtos.GatewayResponse;
import br.com.pluviffar.api.gateway.repositories.GatewayRepository;
import br.com.pluviffar.api.shared.web.PageResponse;
import br.com.pluviffar.api.shared.web.QuerySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/** PRODUCER vê só os próprios; ADMIN vê todos e pode filtrar por dono e incluir excluídos. */
@Service
@RequiredArgsConstructor
public class ListGatewaysUseCase {

    public static final Set<String> SORTABLE_FIELDS = Set.of("name", "hardwareId", "createdAt", "updatedAt");

    private final GatewayRepository gatewayRepository;

    @Transactional(readOnly = true)
    public PageResponse<GatewayResponse> execute(UUID ownerId, String search, boolean includeDeleted, Pageable pageable) {
        QuerySupport.requireSortable(pageable, SORTABLE_FIELDS);
        UUID scope = CurrentUser.ownerScope();
        boolean admin = scope == null;
        var spec = GatewayRepository.visible(scope, admin && includeDeleted)
                .and(GatewayRepository.filter(admin ? ownerId : null, search));
        return PageResponse.of(gatewayRepository.findAll(spec, pageable), GatewayResponse::from);
    }
}
