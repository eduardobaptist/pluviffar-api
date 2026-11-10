package br.com.pluviffar.api.telemetry.repositories;

import br.com.pluviffar.api.telemetry.entities.Reading;
import br.com.pluviffar.api.telemetry.entities.TelemetryId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Leituras brutas (somente leitura). Agregações ficam em {@link TelemetryQueries}. */
public interface ReadingRepository extends JpaRepository<Reading, TelemetryId> {

    @Query("select r from Reading r where r.id.stationId = :stationId and r.id.measuredAt >= :from and r.id.measuredAt < :to")
    Page<Reading> findInRange(@Param("stationId") UUID stationId, @Param("from") Instant from, @Param("to") Instant to,
                              Pageable pageable);

    Optional<Reading> findFirstByIdStationIdOrderByIdMeasuredAtDesc(UUID stationId);
}
