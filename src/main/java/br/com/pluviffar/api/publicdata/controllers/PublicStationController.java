package br.com.pluviffar.api.publicdata.controllers;

import br.com.pluviffar.api.publicdata.dtos.PublicStationDetail;
import br.com.pluviffar.api.publicdata.dtos.PublicStationSummary;
import br.com.pluviffar.api.publicdata.useCases.GetPublicStationUseCase;
import br.com.pluviffar.api.publicdata.useCases.ListPublicStationsUseCase;
import br.com.pluviffar.api.shared.exceptions.ApiErrorDTO;
import br.com.pluviffar.api.telemetry.dtos.PrecipitationBucket;
import br.com.pluviffar.api.telemetry.dtos.PrecipitationSeriesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/stations")
@RequiredArgsConstructor
@Tag(name = "Área pública", description = "Sem login. Só estações marcadas como públicas pelo dono: nome, localização e chuva. "
        + "Nunca expõe dados do dono nem o chip ID.")
public class PublicStationController {

    private static final String TIMEZONE_DESCRIPTION = "Fuso IANA para alinhar dias/semanas/meses e \"hoje\". Padrão: America/Sao_Paulo.";

    private final ListPublicStationsUseCase listPublicStationsUseCase;
    private final GetPublicStationUseCase getPublicStationUseCase;

    @GetMapping
    @Operation(summary = "Listar estações públicas",
            description = "Todas as estações públicas (sem paginação, para o mapa), com última leitura e chuva das últimas 24 h.")
    @ApiResponse(responseCode = "200", description = "Estações públicas, em ordem de nome.")
    public List<PublicStationSummary> list() {
        return listPublicStationsUseCase.execute();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhar estação pública", description = "Localização e chuva acumulada hoje, 24 h, 7 e 30 dias.")
    @ApiResponse(responseCode = "200", description = "Estação pública.")
    @ApiResponse(responseCode = "404", description = "STATION_NOT_FOUND (inexistente ou não pública).",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public PublicStationDetail get(
            @PathVariable UUID id,
            @Parameter(description = TIMEZONE_DESCRIPTION, example = "America/Sao_Paulo")
            @RequestParam(required = false) String timezone) {
        return getPublicStationUseCase.detail(id, timezone);
    }

    @GetMapping("/{id}/precipitation")
    @Operation(summary = "Série de chuva de estação pública",
            description = "Mesma agregação da área logada (hora, dia, semana ou mês). Buckets sem leitura não aparecem.")
    @ApiResponse(responseCode = "200", description = "Série.")
    @ApiResponse(responseCode = "404", description = "STATION_NOT_FOUND (inexistente ou não pública).",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    @ApiResponse(responseCode = "400", description = "INVALID_PERIOD, PERIOD_TOO_LONG ou INVALID_PARAMETER.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public PrecipitationSeriesResponse precipitation(
            @PathVariable UUID id,
            @Parameter(description = "Granularidade (padrão DAY).") @RequestParam(required = false) PrecipitationBucket bucket,
            @Parameter(description = "Início (inclusivo), ISO-8601.", example = "2026-10-01T03:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "Fim (exclusivo), ISO-8601. Padrão: agora.", example = "2026-11-01T03:00:00Z")
            @RequestParam(required = false) Instant to,
            @Parameter(description = TIMEZONE_DESCRIPTION, example = "America/Sao_Paulo")
            @RequestParam(required = false) String timezone) {
        return getPublicStationUseCase.precipitation(id, bucket, from, to, timezone);
    }
}
