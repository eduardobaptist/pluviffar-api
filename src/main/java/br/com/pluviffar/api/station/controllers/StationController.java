package br.com.pluviffar.api.station.controllers;

import br.com.pluviffar.api.shared.exceptions.ApiErrorDTO;
import br.com.pluviffar.api.shared.web.PageResponse;
import br.com.pluviffar.api.station.dtos.CreateStationRequest;
import br.com.pluviffar.api.station.dtos.StationResponse;
import br.com.pluviffar.api.station.dtos.UpdateStationRequest;
import br.com.pluviffar.api.station.useCases.CreateStationUseCase;
import br.com.pluviffar.api.station.useCases.DeleteStationUseCase;
import br.com.pluviffar.api.station.useCases.FindStationUseCase;
import br.com.pluviffar.api.station.useCases.ListStationsUseCase;
import br.com.pluviffar.api.station.useCases.UpdateStationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
@Tag(name = "Estações", description = "Estações (pluviômetros) do produtor. PRODUCER gerencia só as próprias (as de outro produtor "
        + "respondem 404); ADMIN gerencia todas.")
public class StationController {

    private final CreateStationUseCase createStationUseCase;
    private final ListStationsUseCase listStationsUseCase;
    private final FindStationUseCase findStationUseCase;
    private final UpdateStationUseCase updateStationUseCase;
    private final DeleteStationUseCase deleteStationUseCase;

    @PostMapping
    @Operation(summary = "Cadastrar estação",
            description = "Registra o chip ID do pluviômetro, vinculado a um gateway do mesmo produtor. "
                    + "Só pacotes MQTT de equipamentos cadastrados são gravados.")
    @ApiResponse(responseCode = "201", description = "Estação cadastrada (header Location aponta para ela).")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR (inclui locationComplete e publicWithLocation).",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    @ApiResponse(responseCode = "404", description = "GATEWAY_NOT_FOUND (gateway inexistente, excluído ou de outro produtor).",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    @ApiResponse(responseCode = "409", description = "HARDWARE_ID_ALREADY_REGISTERED.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public ResponseEntity<StationResponse> create(@Valid @RequestBody CreateStationRequest request) {
        StationResponse created = createStationUseCase.execute(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping
    @Operation(summary = "Listar estações",
            description = "PRODUCER: só as próprias. ADMIN: todas, com filtro opcional por dono. "
                    + "sort aceita: name, hardwareId, createdAt, updatedAt. Padrão: name,asc.")
    @ApiResponse(responseCode = "200", description = "Página de estações.")
    public PageResponse<StationResponse> list(
            @Parameter(description = "Filtra pelo gateway.") @RequestParam(required = false) UUID gatewayId,
            @Parameter(description = "Somente ADMIN: filtra por produtor dono.") @RequestParam(required = false) UUID ownerId,
            @Parameter(description = "Filtra por visibilidade pública.") @RequestParam(required = false) Boolean isPublic,
            @Parameter(description = "Busca por parte do nome ou do chip ID.", example = "talhão")
            @RequestParam(required = false) String search,
            @Parameter(description = "Somente ADMIN: inclui estações excluídas.")
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return listStationsUseCase.execute(gatewayId, ownerId, isPublic, search, includeDeleted, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhar estação")
    @ApiResponse(responseCode = "200", description = "Estação.")
    @ApiResponse(responseCode = "404", description = "STATION_NOT_FOUND (inexistente, excluída ou de outro produtor).",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public StationResponse get(@PathVariable UUID id) {
        return findStationUseCase.execute(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar estação",
            description = "Substitui nome, descrição, localização, visibilidade pública e gateway. "
                    + "Ao trocar de gateway, a estação passa a ser do dono do novo gateway.")
    @ApiResponse(responseCode = "200", description = "Estação atualizada.")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR (inclui locationComplete e publicWithLocation).",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    @ApiResponse(responseCode = "404", description = "STATION_NOT_FOUND ou GATEWAY_NOT_FOUND.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public StationResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateStationRequest request) {
        return updateStationUseCase.execute(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir estação",
            description = "Exclusão lógica: as leituras gravadas são mantidas e o chip ID fica livre para novo cadastro. "
                    + "A estação sai da área pública.")
    @ApiResponse(responseCode = "204", description = "Estação excluída.")
    @ApiResponse(responseCode = "404", description = "STATION_NOT_FOUND.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteStationUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}
