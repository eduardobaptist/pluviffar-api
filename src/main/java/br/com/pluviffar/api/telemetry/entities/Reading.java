package br.com.pluviffar.api.telemetry.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Leitura de chuva (tópico dados) — hypertable, escrita só pelo worker.
 * Somente leitura na API; agregações (time_bucket) via query nativa/JdbcTemplate, não JPQL.
 */
@Entity
@Immutable
@Table(name = "readings", schema = "telemetry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reading {

    @EmbeddedId
    private TelemetryId id;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "tips_per_minute", nullable = false)
    private Integer tipsPerMinute;

    /** Chuva no intervalo, em mm. */
    @Column(name = "precipitation_mm", nullable = false, precision = 6, scale = 2)
    private BigDecimal precipitationMm;

    @Column(nullable = false)
    private Long seq;
}
