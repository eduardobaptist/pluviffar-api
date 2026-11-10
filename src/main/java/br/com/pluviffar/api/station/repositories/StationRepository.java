package br.com.pluviffar.api.station.repositories;

import br.com.pluviffar.api.shared.web.QuerySupport;
import br.com.pluviffar.api.station.entities.Station;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface StationRepository extends JpaRepository<Station, UUID>, JpaSpecificationExecutor<Station> {

    /** Chip ID já cadastrado (entre os não excluídos), de qualquer dono. */
    boolean existsByHardwareIdAndDeletedAtIsNull(String hardwareId);

    boolean existsByGateway_IdAndDeletedAtIsNull(UUID gatewayId);

    @Override
    @EntityGraph(attributePaths = {"gateway", "owner"})
    Page<Station> findAll(Specification<Station> spec, Pageable pageable);

    /**
     * Estações visíveis para a requisição.
     *
     * @param ownerScope     dono obrigatório (PRODUCER) ou null (ADMIN) — ver CurrentUser.ownerScope()
     * @param includeDeleted inclui excluídas (soft delete); só deve ser true para ADMIN
     */
    static Specification<Station> visible(UUID ownerScope, boolean includeDeleted) {
        List<Specification<Station>> specs = new ArrayList<>();
        if (ownerScope != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerScope));
        }
        if (!includeDeleted) {
            specs.add((root, query, cb) -> cb.isNull(root.get("deletedAt")));
        }
        return Specification.allOf(specs);
    }

    static Specification<Station> byId(UUID id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    /** Filtros opcionais da listagem: gateway, dono (ADMIN), visibilidade pública e busca em nome/chip ID. */
    static Specification<Station> filter(UUID gatewayId, UUID ownerId, Boolean isPublic, String search) {
        List<Specification<Station>> specs = new ArrayList<>();
        if (gatewayId != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("gateway").get("id"), gatewayId));
        }
        if (ownerId != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId));
        }
        if (isPublic != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("isPublic"), isPublic));
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
