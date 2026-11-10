package br.com.pluviffar.api.gateway.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;
import java.util.UUID;

@Schema(description = "Cadastro de gateway. Só pacotes de equipamentos cadastrados são gravados pelo worker.")
public record CreateGatewayRequest(
        @Schema(description = "Chip ID do gateway, como aparece no tópico MQTT (normalizado para minúsculas). Não pode ser alterado depois.",
                example = "gt-570db4", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 32)
        @NotBlank(message = "{device.hardware-id.required}")
        @Pattern(regexp = "^[a-z0-9-]{1,32}$", message = "{device.hardware-id.pattern}")
        String hardwareId,

        @Schema(description = "Nome.", example = "Gateway da sede", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 100)
        @NotBlank(message = "{device.name.required}")
        @Size(max = 100, message = "{device.name.size}")
        String name,

        @Schema(description = "Descrição livre.", example = "Instalado no galpão principal", maxLength = 1000)
        @Size(max = 1000, message = "{device.description.size}")
        String description,

        @Schema(description = "Produtor dono. Obrigatório para ADMIN; PRODUCER deve omitir (o dono é ele mesmo).",
                example = "019a6f3e-8b1c-7d2e-9f40-1a2b3c4d5e6f")
        UUID ownerId
) {
    public CreateGatewayRequest {
        hardwareId = hardwareId == null ? null : hardwareId.trim().toLowerCase(Locale.ROOT);
        name = name == null ? null : name.trim();
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
