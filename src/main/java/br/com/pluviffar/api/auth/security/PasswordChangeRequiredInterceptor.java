package br.com.pluviffar.api.auth.security;

import br.com.pluviffar.api.shared.exceptions.BusinessException;
import br.com.pluviffar.api.shared.exceptions.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enquanto must_change_password estiver ligado, bloqueia a API (403 PASSWORD_CHANGE_REQUIRED).
 * Registrado para /api/** exceto /api/auth/** (login, me, logout, troca de senha) e /api/public/**.
 */
@Component
public class PasswordChangeRequiredInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        CurrentUser.find()
                .filter(AuthenticatedUser::isMustChangePassword)
                .ifPresent(user -> {
                    throw new BusinessException(ErrorCode.PASSWORD_CHANGE_REQUIRED);
                });
        return true;
    }
}
