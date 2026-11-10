package br.com.pluviffar.api.user.repositories;

import br.com.pluviffar.api.user.entities.Role;
import br.com.pluviffar.api.user.entities.User;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** O email já deve vir normalizado (User.normalizeEmail). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);

    /** Filtros opcionais da listagem: busca em nome/email, role e ativo. */
    static Specification<User> filter(String search, Role role, Boolean active) {
        List<Specification<User>> specs = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, '\\'),
                    cb.like(root.get("email"), pattern, '\\')));
        }
        if (role != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (active != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        return Specification.allOf(specs);
    }
}
