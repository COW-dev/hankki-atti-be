package com.hankkiatti.global.security;

import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 권한 부족을 ApiResult 형식으로 응답한다. 비밀번호 변경이 필요한 계정이면 그 안내를 준다.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final HandlerExceptionResolver handlerExceptionResolver;

    public JsonAccessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        AuthErrorType errorType = requiresPasswordChange() ? AuthErrorType.PASSWORD_CHANGE_REQUIRED
                : AuthErrorType.ACCESS_DENIED;
        handlerExceptionResolver.resolveException(request, response, null,
                new AuthException(errorType, "uri=" + request.getRequestURI()));
    }

    private boolean requiresPasswordChange() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> AuthAuthorities.PASSWORD_CHANGE_ONLY.equals(authority.getAuthority()));
    }
}
