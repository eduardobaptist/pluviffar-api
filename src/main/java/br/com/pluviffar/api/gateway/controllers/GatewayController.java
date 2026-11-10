package br.com.pluviffar.api.gateway.controllers;

import br.com.pluviffar.api.gateway.dtos.CreateGatewayRequest;
import br.com.pluviffar.api.gateway.dtos.GatewayResponse;
import br.com.pluviffar.api.gateway.dtos.UpdateGatewayRequest;
import br.com.pluviffar.api.gateway.useCases.CreateGatewayUseCase;
import br.com.pluviffar.api.gateway.useCases.DeleteGatewayUseCase;
import br.com.pluviffar.api.gateway.useCases.FindGatewayUseCase;
import br.com.pluviffar.api.gateway.useCases.ListGatewaysUseCase;
import br.com.pluviffar.api.gateway.useCases.UpdateGatewayUseCase;
import br.com.pluviffar.api.shared.exceptions.ApiErrorDTO;
import br.com.pluviffar.api.shared.web.PageResponse;
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
@RequestMapping("/api/gateways")
@RequiredArgsConstructor
@Tag(name = "Gateways", description = "Gateways do produtor. PRODUCER gerencia só os próprios (os de outro produtor respondem 404); "
        + "ADMIN gerencia todos.")
public class GatewayController {

    private final CreateGatewayUseCase createGatewayUseCase;
    private final ListGatewaysUseCase listGatewaysUseCase;
    private final FindGatewayUseCase findGatewayUseCase;
    private final UpdateGatewayUseCase updateGatewayUseCase;
    private final DeleteGatewayUseCase deleteGatewayUseCase;

    @PostMapping
    @Operation(summary = "Cadastrar gateway",
            description = "Registra o chip ID do gateway. Só pacotes MQTT de equipamentos cadastrados são gravados. "
                    + "PRODUCER: o dono é ele mesmo (omita ownerId). ADMIN: informe ownerId (produtor ativo).")
    @ApiResponse(responseCode = "201", description = "Gateway cadastrado (header Location aponta para ele).")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, OWNER_REQUIRED (ADMIN sem ownerId) ou INVALID_OWNER.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    @ApiResponse(responseCode = "403", description = "ACCESS_DENIED (PRODUCER informando outro dono), CSRF_TOKEN_INVALID ou PASSWORD_CHANGE_REQUIRED.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    @ApiResponse(responseCode = "409", description = "HARDWARE_ID_ALREADY_REGISTERED.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public ResponseEntity<GatewayResponse> create(@Valid @RequestBody CreateGatewayRequest request) {
        GatewayResponse created = createGatewayUseCase.execute(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping
    @Operation(summary = "Listar gateways",
            description = "PRODUCER: só os próprios. ADMIN: todos, com filtro opcional por dono. "
                    + "sort aceita: name, hardwareId, createdAt, updatedAt. Padrão: name,asc.")
    @ApiResponse(responseCode = "200", description = "Página de gateways.")
    public PageResponse<GatewayResponse> list(
            @Parameter(description = "Somente ADMIN: filtra por produtor dono.") @RequestParam(required = false) UUID ownerId,
            @Parameter(description = "Busca por parte do nome ou do chip ID.", example = "sede")
            @RequestParam(required = false) String search,
            @Parameter(description = "Somente ADMIN: inclui gateways excluídos.")
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return listGatewaysUseCase.execute(ownerId, search, includeDeleted, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhar gateway")
    @ApiResponse(responseCode = "200", description = "Gateway.")
    @ApiResponse(responseCode = "404", description = "GATEWAY_NOT_FOUND (inexistente, excluído ou de outro produtor).",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public GatewayResponse get(@PathVariable UUID id) {
        return findGatewayUseCase.execute(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar gateway",
            description = "Substitui nome e descrição. ADMIN pode transferir o gateway para outro produtor (ownerId): "
                    + "as estações dele acompanham.")
    @ApiResponse(responseCode = "200", description = "Gateway atualizado.")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR ou INVALID_OWNER.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    @ApiResponse(responseCode = "403", description = "ACCESS_DENIED (PRODUCER tentando transferir), CSRF_TOKEN_INVALID ou PASSWORD_CHANGE_REQUIRED.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    @ApiResponse(responseCode = "404", description = "GATEWAY_NOT_FOUND.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public GatewayResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateGatewayRequest request) {
        return updateGatewayUseCase.execute(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir gateway",
            description = "Exclusão lógica: os dados gravados são mantidos e o chip ID fica livre para novo cadastro. "
                    + "Não é permitido enquanto o gateway tiver estações ativas.")
    @ApiResponse(responseCode = "204", description = "Gateway excluído.")
    @ApiResponse(responseCode = "404", description = "GATEWAY_NOT_FOUND.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    @ApiResponse(responseCode = "409", description = "GATEWAY_HAS_ACTIVE_STATIONS.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteGatewayUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}
