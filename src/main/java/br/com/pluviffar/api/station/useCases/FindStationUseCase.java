package br.com.pluviffar.api.station.useCases;

import br.com.pluviffar.api.auth.security.CurrentUser;
import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import br.com.pluviffar.api.station.dtos.StationResponse;
import br.com.pluviffar.api.station.entities.Station;
import br.com.pluviffar.api.station.repositories.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Busca com checagem de posse: estação de outro produtor (ou excluída) responde 404, sem revelar que existe.
 * O ADMIN também enxerga excluídas no detalhe, mas não pode alterá-las.
 */
@Service
@RequiredArgsConstructor
public class FindStationUseCase {

    private final StationRepository stationRepository;

    @Transactional(readOnly = true)
    public StationResponse execute(UUID id) {
        UUID scope = CurrentUser.ownerScope();
        return StationResponse.from(find(id, scope, scope == null));
    }

    /** Estação ativa acessível pelo usuário atual (para alterar/excluir). */
    public Station getActiveOrThrow(UUID id) {
        return find(id, CurrentUser.ownerScope(), false);
    }

    private Station find(UUID id, UUID scope, boolean includeDeleted) {
        return stationRepository.findOne(StationRepository.visible(scope, includeDeleted).and(StationRepository.byId(id)))
                .orElseThrow(() -> new BusinessException(ErrorCode.STATION_NOT_FOUND));
    }
}
