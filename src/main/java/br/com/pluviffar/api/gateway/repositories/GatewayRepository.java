package br.com.pluviffar.api.gateway.repositories;

import br.com.pluviffar.api.gateway.entities.Gateway;
import br.com.pluviffar.api.shared.web.QuerySupport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface GatewayRepository extends JpaRepository<Gateway, UUID>, JpaSpecificationExecutor<Gateway> {

    /** Chip ID já cadastrado (entre os não excluídos), de qualquer dono. */
    boolean existsByHardwareIdAndDeletedAtIsNull(String hardwareId);

    @Override
    @EntityGraph(attributePaths = "owner")
    Page<Gateway> findAll(Specification<Gateway> spec, Pageable pageable);

    /**
     * Gateways visíveis para a requisição.
     *
     * @param ownerScope     dono obrigatório (PRODUCER) ou null (ADMIN, sem restrição) — ver CurrentUser.ownerScope()
     * @param includeDeleted inclui excluídos (soft delete); só deve ser true para ADMIN
     */
    static Specification<Gateway> visible(UUID ownerScope, boolean includeDeleted) {
        List<Specification<Gateway>> specs = new ArrayList<>();
        if (ownerScope != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerScope));
        }
        if (!includeDeleted) {
            specs.add((root, query, cb) -> cb.isNull(root.get("deletedAt")));
        }
        return Specification.allOf(specs);
    }

    static Specification<Gateway> byId(UUID id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    /** Filtros opcionais da listagem: dono (ADMIN) e busca em nome/chip ID. */
    static Specification<Gateway> filter(UUID ownerId, String search) {
        List<Specification<Gateway>> specs = new ArrayList<>();
        if (ownerId != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId));
        }
        String pattern = QuerySupport.containsPattern(search);
        if (pattern != null) {
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern, QuerySupport.LIKE_ESCAPE),
                    cb.like(root.get("hardwareId"), pattern, QuerySupport.LIKE_ESCAPE)));
        }
        return Specification.allOf(specs);
    }
}
