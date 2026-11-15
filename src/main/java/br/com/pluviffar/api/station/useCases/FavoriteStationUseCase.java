package br.com.pluviffar.api.station.useCases;

import br.com.pluviffar.api.auth.security.CurrentUser;
import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import br.com.pluviffar.api.station.entities.Station;
import br.com.pluviffar.api.user.entities.User;
import br.com.pluviffar.api.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Estação favorita do usuário logado (uma por usuário). Exposta só como StationResponse.favorite: o front decide
 * o que fazer com ela (ex.: mostrar primeiro no dashboard). Marcar/desmarcar usa a mesma checagem de posse do resto
 * do módulo (PRODUCER só as próprias ativas; ADMIN qualquer ativa). A coluna não é limpa quando a estação é excluída
 * ou transferida: como ela some das listagens do usuário, o favorito antigo simplesmente não aparece.
 */
@Service
@RequiredArgsConstructor
public class FavoriteStationUseCase {

    private final FindStationUseCase findStationUseCase;
    private final UserRepository userRepository;

    /** Marca a estação como favorita, substituindo a anterior. Idempotente. */
    @Transactional
    public void mark(UUID stationId) {
        Station station = findStationUseCase.getActiveOrThrow(stationId);
        currentUser().setFavoriteStationId(station.getId());
    }

    /** Desmarca, se for a favorita atual. Idempotente. */
    @Transactional
    public void unmark(UUID stationId) {
        Station station = findStationUseCase.getActiveOrThrow(stationId);
        User user = currentUser();
        if (station.getId().equals(user.getFavoriteStationId())) {
            user.setFavoriteStationId(null);
        }
    }

    /** Favorita gravada; usada para preencher o campo "favorite" das estações que o usuário está vendo. */
    @Transactional(readOnly = true)
    public UUID storedFavoriteId() {
        return userRepository.findFavoriteStationId(CurrentUser.get().getId()).orElse(null);
    }

    private User currentUser() {
        return userRepository.findById(CurrentUser.get().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }
}
