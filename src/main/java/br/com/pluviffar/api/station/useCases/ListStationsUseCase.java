package br.com.pluviffar.api.station.useCases;

import br.com.pluviffar.api.auth.security.CurrentUser;
import br.com.pluviffar.api.shared.web.PageResponse;
import br.com.pluviffar.api.shared.web.QuerySupport;
import br.com.pluviffar.api.station.dtos.StationResponse;
import br.com.pluviffar.api.station.repositories.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/** PRODUCER vê só as próprias; ADMIN vê todas e pode filtrar por dono e incluir excluídas. */
@Service
@RequiredArgsConstructor
public class ListStationsUseCase {

    public static final Set<String> SORTABLE_FIELDS = Set.of("name", "hardwareId", "createdAt", "updatedAt");

    private final StationRepository stationRepository;

    @Transactional(readOnly = true)
    public PageResponse<StationResponse> execute(UUID gatewayId, UUID ownerId, Boolean isPublic, String search,
                                                 boolean includeDeleted, Pageable pageable) {
        QuerySupport.requireSortable(pageable, SORTABLE_FIELDS);
        UUID scope = CurrentUser.ownerScope();
        boolean admin = scope == null;
        var spec = StationRepository.visible(scope, admin && includeDeleted)
                .and(StationRepository.filter(gatewayId, admin ? ownerId : null, isPublic, search));
        return PageResponse.of(stationRepository.findAll(spec, pageable), StationResponse::from);
    }
}
