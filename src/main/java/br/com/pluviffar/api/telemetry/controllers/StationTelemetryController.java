package br.com.pluviffar.api.telemetry.controllers;

import br.com.pluviffar.api.shared.exceptions.ApiErrorDTO;
import br.com.pluviffar.api.shared.web.PageResponse;
import br.com.pluviffar.api.telemetry.dtos.ConfigReportResponse;
import br.com.pluviffar.api.telemetry.dtos.DeviceStatusResponse;
import br.com.pluviffar.api.telemetry.dtos.PrecipitationBucket;
import br.com.pluviffar.api.telemetry.dtos.PrecipitationSeriesResponse;
import br.com.pluviffar.api.telemetry.dtos.ReadingResponse;
import br.com.pluviffar.api.telemetry.dtos.StationOverviewResponse;
import br.com.pluviffar.api.telemetry.useCases.GetStationOverviewUseCase;
import br.com.pluviffar.api.telemetry.useCases.GetStationPrecipitationUseCase;
import br.com.pluviffar.api.telemetry.useCases.ListStationTelemetryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/stations/{id}")
@RequiredArgsConstructor
@Tag(name = "Telemetria", description = "Dados gravados pelo worker para uma estação. Mesma regra de posse das estações "
        + "(de outro produtor → 404); o ADMIN também lê dados de estações excluídas. Períodos são [from, to) em ISO-8601 "
        + "(ex.: 2026-11-10T00:00:00Z).")
@ApiResponse(responseCode = "404", description = "STATION_NOT_FOUND.",
        content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
@ApiResponse(responseCode = "400", description = "INVALID_PERIOD, PERIOD_TOO_LONG ou INVALID_PARAMETER.",
        content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
public class StationTelemetryController {

    private static final String TIMEZONE_DESCRIPTION = "Fuso IANA para alinhar dias/semanas/meses e \"hoje\". Padrão: America/Sao_Paulo.";

    private final ListStationTelemetryUseCase listStationTelemetryUseCase;
    private final GetStationPrecipitationUseCase getStationPrecipitationUseCase;
    private final GetStationOverviewUseCase getStationOverviewUseCase;

    @GetMapping("/overview")
    @Operation(summary = "Resumo da estação",
            description = "Última leitura, último status e chuva acumulada hoje, nas últimas 24 h, 7 e 30 dias.")
    @ApiResponse(responseCode = "200", description = "Resumo.")
    public StationOverviewResponse overview(
            @PathVariable UUID id,
            @Parameter(description = TIMEZONE_DESCRIPTION, example = "America/Sao_Paulo")
            @RequestParam(required = false) String timezone) {
        return getStationOverviewUseCase.execute(id, timezone);
    }

    @GetMapping("/precipitation")
    @Operation(summary = "Série de chuva agregada",
            description = "Chuva somada por hora, dia, semana ou mês (no fuso pedido). Sem from, usa o período padrão da "
                    + "granularidade; sem to, até agora. Buckets sem leitura não aparecem.")
    @ApiResponse(responseCode = "200", description = "Série.")
    public PrecipitationSeriesResponse precipitation(
            @PathVariable UUID id,
            @Parameter(description = "Granularidade (padrão DAY).") @RequestParam(required = false) PrecipitationBucket bucket,
            @Parameter(description = "Início (inclusivo), ISO-8601.", example = "2026-10-01T03:00:00Z")
            @RequestParam(required = false) Instant from,
            @Parameter(description = "Fim (exclusivo), ISO-8601. Padrão: agora.", example = "2026-11-01T03:00:00Z")
            @RequestParam(required = false) Instant to,
            @Parameter(description = TIMEZONE_DESCRIPTION, example = "America/Sao_Paulo")
            @RequestParam(required = false) String timezone) {
        return getStationPrecipitationUseCase.execute(id, bucket, from, to, timezone);
    }

    @GetMapping("/readings")
    @Operation(summary = "Leituras brutas",
            description = "Leituras do tópico dados, da mais recente para a mais antiga (sort é ignorado). "
                    + "Padrão: últimas 24 h; máximo 93 dias.")
    @ApiResponse(responseCode = "200", description = "Página de leituras.")
    public PageResponse<ReadingResponse> readings(
            @PathVariable UUID id,
            @Parameter(description = "Início (inclusivo), ISO-8601. Padrão: to − 24 h.") @RequestParam(required = false) Instant from,
            @Parameter(description = "Fim (exclusivo), ISO-8601. Padrão: agora.") @RequestParam(required = false) Instant to,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return listStationTelemetryUseCase.readings(id, from, to, pageable);
    }

    @GetMapping("/status")
    @Operation(summary = "Status do dispositivo",
            description = "Bateria, sinal (RSSI) e uptime (tópico status), do mais recente para o mais antigo (sort é ignorado). "
                    + "Padrão: últimas 24 h; máximo 93 dias.")
    @ApiResponse(responseCode = "200", description = "Página de status.")
    public PageResponse<DeviceStatusResponse> status(
            @PathVariable UUID id,
            @Parameter(description = "Início (inclusivo), ISO-8601. Padrão: to − 24 h.") @RequestParam(required = false) Instant from,
            @Parameter(description = "Fim (exclusivo), ISO-8601. Padrão: agora.") @RequestParam(required = false) Instant to,
            @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return listStationTelemetryUseCase.status(id, from, to, pageable);
    }

    @GetMapping("/config-reports")
    @Operation(summary = "Histórico de configuração",
            description = "Versões de firmware/hardware, intervalo de envio e mm por basculada reportados pelo dispositivo "
                    + "(tópico conf), do mais recente para o mais antigo. O estado atual também está no detalhe da estação.")
    @ApiResponse(responseCode = "200", description = "Página de configurações.")
    public PageResponse<ConfigReportResponse> configReports(
            @PathVariable UUID id,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return listStationTelemetryUseCase.configReports(id, pageable);
    }
}
