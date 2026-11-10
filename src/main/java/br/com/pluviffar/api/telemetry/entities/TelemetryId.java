package br.com.pluviffar.api.telemetry.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Chave das hypertables: (station_id, measured_at) — a coluna de tempo faz parte da PK. */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TelemetryId implements Serializable {

    @Column(name = "station_id", nullable = false)
    private UUID stationId;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;
}
