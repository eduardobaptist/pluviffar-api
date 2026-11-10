package br.com.pluviffar.api.user.repositories;

import br.com.pluviffar.api.shared.web.QuerySupport;
import br.com.pluviffar.api.user.entities.Role;
import br.com.pluviffar.api.user.entities.User;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;
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
        String pattern = QuerySupport.containsPattern(search);
        if (pattern != null) {
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, QuerySupport.LIKE_ESCAPE),
                    cb.like(root.get("email"), pattern, QuerySupport.LIKE_ESCAPE)));
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
