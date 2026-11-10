package br.com.pluviffar.api.station.entities;

import br.com.pluviffar.api.gateway.entities.Gateway;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Estação = pluviômetro. hardwareId = campo "id" do payload MQTT (ex.: 580db2).
 * owner é sempre o dono do gateway (FK composta no banco); ao trocar de gateway, atualize os dois.
 * Soft delete via deletedAt.
 */
@Entity
@Table(name = "stations", schema = "app")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Station {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "hardware_id", nullable = false, length = 32)
    private String hardwareId;

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gateway_id", nullable = false)
    private Gateway gateway;

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    private Double latitude;

    private Double longitude;

    /** Visível na área pública (sem dados do dono). */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    // estado atual reportado pelo tópico conf (escrito pelo worker)
    @Column(name = "fw_version", length = 20)
    private String fwVersion;

    @Column(name = "hw_version", length = 20)
    private String hwVersion;

    @Column(name = "send_interval_s")
    private Integer sendIntervalS;

    @Column(name = "mm_per_tip", precision = 5, scale = 3)
    private BigDecimal mmPerTip;

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

    public Station(String hardwareId, Gateway gateway, String name) {
        this.hardwareId = hardwareId;
        this.name = name;
        assignGateway(gateway);
    }

    /** Vincula ao gateway e herda o dono dele (exigência da FK composta). */
    public void assignGateway(Gateway gateway) {
        this.gateway = gateway;
        this.owner = gateway.getOwner();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
