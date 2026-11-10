package br.com.pluviffar.api.user.controllers;

import br.com.pluviffar.api.shared.exceptions.ApiErrorDTO;
import br.com.pluviffar.api.shared.web.PageResponse;
import br.com.pluviffar.api.user.dtos.CreateUserRequest;
import br.com.pluviffar.api.user.dtos.ResetPasswordRequest;
import br.com.pluviffar.api.user.dtos.UpdateUserRequest;
import br.com.pluviffar.api.user.dtos.UpdateUserStatusRequest;
import br.com.pluviffar.api.user.dtos.UserResponse;
import br.com.pluviffar.api.user.entities.Role;
import br.com.pluviffar.api.user.useCases.CreateUserUseCase;
import br.com.pluviffar.api.user.useCases.FindUserUseCase;
import br.com.pluviffar.api.user.useCases.ListUsersUseCase;
import br.com.pluviffar.api.user.useCases.ResetUserPasswordUseCase;
import br.com.pluviffar.api.user.useCases.UpdateUserStatusUseCase;
import br.com.pluviffar.api.user.useCases.UpdateUserUseCase;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Gestão de usuários — somente ADMIN. Não há auto-cadastro.")
@ApiResponse(responseCode = "403", description = "ACCESS_DENIED (não é ADMIN), CSRF_TOKEN_INVALID ou PASSWORD_CHANGE_REQUIRED.",
        content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
public class UserController {

    private final CreateUserUseCase createUserUseCase;
    private final ListUsersUseCase listUsersUseCase;
    private final FindUserUseCase findUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final UpdateUserStatusUseCase updateUserStatusUseCase;
    private final ResetUserPasswordUseCase resetUserPasswordUseCase;

    @PostMapping
    @Operation(summary = "Cadastrar usuário",
            description = "Cria o usuário com senha provisória; ele precisa trocá-la no primeiro login.")
    @ApiResponse(responseCode = "201", description = "Usuário criado (header Location aponta para ele).")
    @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_IN_USE.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = createUserUseCase.execute(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @GetMapping
    @Operation(summary = "Listar usuários",
            description = "Lista paginada com filtros opcionais. sort aceita: name, email, role, active, createdAt, updatedAt "
                    + "(ex.: sort=createdAt,desc). Padrão: name,asc.")
    @ApiResponse(responseCode = "200", description = "Página de usuários.")
    public PageResponse<UserResponse> list(
            @Parameter(description = "Busca por parte do nome ou do email (sem diferenciar maiúsculas).", example = "maria")
            @RequestParam(required = false) String search,
            @Parameter(description = "Filtra por perfil.") @RequestParam(required = false) Role role,
            @Parameter(description = "Filtra por ativo/desativado.") @RequestParam(required = false) Boolean active,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return listUsersUseCase.execute(search, role, active, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhar usuário")
    @ApiResponse(responseCode = "200", description = "Usuário.")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public UserResponse get(@PathVariable UUID id) {
        return findUserUseCase.execute(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar usuário",
            description = "Substitui nome, email e perfil. Trocar o perfil encerra as sessões do usuário. "
                    + "O ADMIN não pode alterar o próprio perfil.")
    @ApiResponse(responseCode = "200", description = "Usuário atualizado.")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_IN_USE ou CANNOT_CHANGE_OWN_ROLE.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return updateUserUseCase.execute(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Ativar/desativar usuário",
            description = "Desativar impede o login e encerra imediatamente todas as sessões do usuário. "
                    + "O ADMIN não pode desativar a si mesmo.")
    @ApiResponse(responseCode = "200", description = "Status atualizado.")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    @ApiResponse(responseCode = "409", description = "CANNOT_DEACTIVATE_SELF.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public UserResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return updateUserStatusUseCase.execute(id, request.active());
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Redefinir senha do usuário",
            description = "Define uma senha provisória (troca obrigatória no próximo login) e encerra todas as sessões do usuário.")
    @ApiResponse(responseCode = "204", description = "Senha redefinida.")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
        resetUserPasswordUseCase.execute(id, request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
