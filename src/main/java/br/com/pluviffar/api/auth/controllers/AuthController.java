package br.com.pluviffar.api.auth.controllers;

import br.com.pluviffar.api.auth.dtos.ChangePasswordRequest;
import br.com.pluviffar.api.auth.dtos.CsrfTokenResponse;
import br.com.pluviffar.api.auth.dtos.CurrentUserResponse;
import br.com.pluviffar.api.auth.dtos.LoginRequest;
import br.com.pluviffar.api.auth.useCases.ChangeOwnPasswordUseCase;
import br.com.pluviffar.api.auth.useCases.GetCurrentUserUseCase;
import br.com.pluviffar.api.auth.useCases.LoginUseCase;
import br.com.pluviffar.api.auth.useCases.LogoutUseCase;
import br.com.pluviffar.api.shared.exceptions.ApiErrorDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Sessão por cookie (SESSION) + CSRF. Fluxo: GET /csrf → POST /login → GET /me → POST /logout.")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final LogoutUseCase logoutUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final ChangeOwnPasswordUseCase changeOwnPasswordUseCase;

    @GetMapping("/csrf")
    @Operation(summary = "Obter token CSRF",
            description = "Emite o cookie XSRF-TOKEN e devolve o mesmo valor no corpo. Chame ao abrir a aplicação, antes do login. "
                    + "O cookie é reemitido em toda resposta, inclusive quando o token é renovado após o login.")
    @ApiResponse(responseCode = "200", description = "Token emitido.")
    public CsrfTokenResponse csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @PostMapping("/login")
    @Operation(summary = "Login",
            description = "Autentica por email e senha e cria a sessão (cookie SESSION, HttpOnly). Requer o header X-XSRF-TOKEN. "
                    + "O ID da sessão e o token CSRF são renovados: use o novo cookie XSRF-TOKEN a partir daqui. "
                    + "Se mustChangePassword = true, leve o usuário à troca de senha.")
    @ApiResponse(responseCode = "200", description = "Login efetuado.")
    @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS: email ou senha inválidos (mensagem única, não revela se o usuário existe).",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return loginUseCase.execute(request, httpRequest, httpResponse);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalida a sessão e limpa os cookies SESSION e XSRF-TOKEN. Requer o header X-XSRF-TOKEN. "
            + "Idempotente: sem sessão, também responde 204.")
    @ApiResponse(responseCode = "204", description = "Sessão encerrada.")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        logoutUseCase.execute(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Usuário logado",
            description = "Dados e perfil do usuário da sessão. O front não lê o cookie HttpOnly, então usa este endpoint para saber se há sessão: 401 = sem sessão/expirada.")
    @ApiResponse(responseCode = "200", description = "Usuário logado.")
    public CurrentUserResponse me() {
        return getCurrentUserUseCase.execute();
    }

    @PutMapping("/password")
    @Operation(summary = "Trocar a própria senha",
            description = "Confere a senha atual e grava a nova. Encerra as outras sessões do usuário (a atual continua) e, "
                    + "se a troca era obrigatória, libera o acesso ao resto da API.")
    @ApiResponse(responseCode = "204", description = "Senha alterada.")
    @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR, CURRENT_PASSWORD_INCORRECT ou NEW_PASSWORD_SAME_AS_CURRENT.",
            content = @Content(schema = @Schema(implementation = ApiErrorDTO.class)))
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        changeOwnPasswordUseCase.execute(request, httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }
}
