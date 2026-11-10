package br.com.pluviffar.api.telemetry.repositories;

import br.com.pluviffar.api.telemetry.entities.DeviceStatus;
import br.com.pluviffar.api.telemetry.entities.TelemetryId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DeviceStatusRepository extends JpaRepository<DeviceStatus, TelemetryId> {

    @Query("select s from DeviceStatus s where s.id.stationId = :stationId and s.id.measuredAt >= :from and s.id.measuredAt < :to")
    Page<DeviceStatus> findInRange(@Param("stationId") UUID stationId, @Param("from") Instant from, @Param("to") Instant to,
                                   Pageable pageable);

    Optional<DeviceStatus> findFirstByIdStationIdOrderByIdMeasuredAtDesc(UUID stationId);
}
