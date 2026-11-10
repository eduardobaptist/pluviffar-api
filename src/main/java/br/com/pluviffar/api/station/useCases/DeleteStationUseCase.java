package br.com.pluviffar.api.station.useCases;

import br.com.pluviffar.api.station.entities.Station;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Soft delete: leituras e status continuam no banco; o chip ID fica livre para novo cadastro. */
@Service
@RequiredArgsConstructor
public class DeleteStationUseCase {

    private final FindStationUseCase findStationUseCase;

    @Transactional
    public void execute(UUID id) {
        Station station = findStationUseCase.getActiveOrThrow(id);
        station.setDeletedAt(Instant.now());
        station.setPublic(false); // excluída nunca aparece na área pública
    }
}
