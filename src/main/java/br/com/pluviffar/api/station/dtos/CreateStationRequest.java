package br.com.pluviffar.api.station.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;
import java.util.UUID;

@Schema(description = "Cadastro de estação (pluviômetro). Só pacotes de equipamentos cadastrados são gravados pelo worker. "
        + "O dono é sempre o dono do gateway.")
public record CreateStationRequest(
        @Schema(description = "Chip ID do pluviômetro, como vem no campo id do payload (normalizado para minúsculas). Não pode ser alterado depois.",
                example = "580db2", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 32)
        @NotBlank(message = "{device.hardware-id.required}")
        @Pattern(regexp = "^[a-z0-9-]{1,32}$", message = "{device.hardware-id.pattern}")
        String hardwareId,

        @Schema(description = "Gateway que recebe os dados desta estação (precisa ser do mesmo produtor). "
                + "ADMIN pode escolher gateway de outro produtor: a estação passa a ser desse produtor.",
                example = "019a6f3e-8b1c-7d2e-9f40-1a2b3c4d5e6f", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{station.gateway.required}")
        UUID gatewayId,

        @Schema(description = "Nome.", example = "Pluviômetro do talhão 3", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
        @NotBlank(message = "{device.name.required}")
        @Size(max = 100, message = "{device.name.size}")
        String name,

        @Schema(description = "Descrição livre.", example = "Ao lado da porteira", maxLength = 1000)
        @Size(max = 1000, message = "{device.description.size}")
        String description,

        @Schema(description = "Latitude em graus decimais (WGS84). Informe junto com a longitude.", example = "-29.6842",
                minimum = "-90", maximum = "90")
        @DecimalMin(value = "-90", message = "{station.latitude.range}")
        @DecimalMax(value = "90", message = "{station.latitude.range}")
        Double latitude,

        @Schema(description = "Longitude em graus decimais (WGS84). Informe junto com a latitude.", example = "-53.8069",
                minimum = "-180", maximum = "180")
        @DecimalMin(value = "-180", message = "{station.longitude.range}")
        @DecimalMax(value = "180", message = "{station.longitude.range}")
        Double longitude,

        @Schema(description = "Mostrar na área pública (só chuva e localização). Exige latitude/longitude. Padrão: false.",
                example = "false")
        Boolean isPublic
) {
    public CreateStationRequest {
        hardwareId = hardwareId == null ? null : hardwareId.trim().toLowerCase(Locale.ROOT);
        name = name == null ? null : name.trim();
        description = description == null || description.isBlank() ? null : description.trim();
        isPublic = isPublic != null && isPublic;
    }

    // erros destas regras aparecem nos campos "locationComplete" e "publicWithLocation"
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "{station.location.incomplete}")
    public boolean isLocationComplete() {
        return (latitude == null) == (longitude == null);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "{station.public.requires-location}")
    public boolean isPublicWithLocation() {
        return !isPublic || latitude != null;
    }
}
