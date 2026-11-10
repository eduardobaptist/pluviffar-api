package br.com.pluviffar.api.gateway.entities;

import br.com.pluviffar.api.user.entities.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Gateway (ESP8266 que publica no broker). hardwareId = segmento do tópico MQTT (ex.: gt-570db4).
 * Soft delete via deletedAt: as consultas devem filtrar deletedAt IS NULL.
 */
@Entity
@Table(name = "gateways", schema = "app")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Gateway {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "hardware_id", nullable = false, length = 32)
    private String hardwareId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    // estado atual reportado pelo tópico conf (escrito pelo worker)
    @Column(name = "fw_version", length = 20)
    private String fwVersion;

    @Column(name = "hw_version", length = 20)
    private String hwVersion;

    @Column(name = "config_reported_at")
    private Instant configReportedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Gateway(String hardwareId, User owner, String name) {
        this.hardwareId = hardwareId;
        this.owner = owner;
        this.name = name;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
