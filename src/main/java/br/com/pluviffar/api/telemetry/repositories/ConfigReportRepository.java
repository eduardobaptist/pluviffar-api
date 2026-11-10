package br.com.pluviffar.api.telemetry.repositories;

import br.com.pluviffar.api.telemetry.entities.ConfigReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConfigReportRepository extends JpaRepository<ConfigReport, Long> {

    Page<ConfigReport> findByStationId(UUID stationId, Pageable pageable);
}
