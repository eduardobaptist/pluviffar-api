package br.com.pluviffar.api.auth.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Revoga sessões de um usuário (desativação, troca/redefinição de senha, troca de perfil).
 * As sessões são indexadas por PRINCIPAL_NAME = ID do usuário (ver {@link AuthenticatedUser#getUsername()}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSessionRevoker {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public void revokeAll(UUID userId) {
        revokeAllExcept(userId, null);
    }

    /** Revoga todas as sessões do usuário, menos keepSessionId (ex.: a sessão em que ele trocou a própria senha). */
    public void revokeAllExcept(UUID userId, String keepSessionId) {
        List<String> toRevoke = sessionRepository.findByPrincipalName(userId.toString()).keySet().stream()
                .filter(id -> !id.equals(keepSessionId))
                .toList();
        toRevoke.forEach(sessionRepository::deleteById);
        log.info("Sessões revogadas para o usuário {}: {}", userId, toRevoke.size());
    }
}
