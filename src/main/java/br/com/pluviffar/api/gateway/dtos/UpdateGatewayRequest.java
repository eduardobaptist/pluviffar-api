package br.com.pluviffar.api.gateway.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Edição de gateway (substitui nome e descrição). O chip ID não muda: para trocar o equipamento, exclua e cadastre outro.")
public record UpdateGatewayRequest(
        @Schema(description = "Nome.", example = "Gateway da sede", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
        @NotBlank(message = "{device.name.required}")
        @Size(max = 100, message = "{device.name.size}")
        String name,

        @Schema(description = "Descrição livre.", example = "Instalado no galpão principal", maxLength = 1000)
        @Size(max = 1000, message = "{device.description.size}")
        String description,

        @Schema(description = "Somente ADMIN: transfere o gateway (e todas as estações dele) para outro produtor. "
                + "Omitir mantém o dono atual.", example = "019a6f3e-8b1c-7d2e-9f40-1a2b3c4d5e6f")
        UUID ownerId
) {
    public UpdateGatewayRequest {
        name = name == null ? null : name.trim();
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
