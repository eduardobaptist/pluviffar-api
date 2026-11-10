package br.com.pluviffar.api.user.useCases;

import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import br.com.pluviffar.api.shared.web.PageResponse;
import br.com.pluviffar.api.user.dtos.UserResponse;
import br.com.pluviffar.api.user.entities.Role;
import br.com.pluviffar.api.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ListUsersUseCase {

    /** Campos permitidos em sort (evita ordenar por colunas sensíveis como password_hash). */
    public static final Set<String> SORTABLE_FIELDS = Set.of("name", "email", "role", "active", "createdAt", "updatedAt");

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> execute(String search, Role role, Boolean active, Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER);
            }
        });
        return PageResponse.of(userRepository.findAll(UserRepository.filter(search, role, active), pageable),
                UserResponse::from);
    }
}
