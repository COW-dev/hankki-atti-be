package com.hankkiatti.domain.auth.controller.admin;

import com.hankkiatti.domain.auth.dto.request.LoginRequestDto;
import com.hankkiatti.domain.auth.dto.response.AdminLoginResponseDto;
import com.hankkiatti.domain.auth.dto.response.TokenResponseDto;
import com.hankkiatti.domain.auth.entity.TokenAudience;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.auth.service.AuthResult;
import com.hankkiatti.domain.auth.service.AuthService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import com.hankkiatti.global.security.RefreshTokenCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController implements AdminAuthControllerDocs {

    private static final TokenAudience AUDIENCE = TokenAudience.ADMIN;

    private final AuthService authService;
    private final RefreshTokenCookies refreshTokenCookies;

    @Override
    @PostMapping("/login")
    public ResponseEntity<ApiResult<AdminLoginResponseDto>> login(@Valid @RequestBody LoginRequestDto request) {
        AuthResult<AdminLoginResponseDto> result = authService.loginAdmin(request);
        return ApiResponse.of(SuccessType.SUCCESS, result.body(),
                withCookie(refreshTokenCookies.create(AUDIENCE, result.refreshToken())));
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<ApiResult<TokenResponseDto>> refresh(HttpServletRequest request) {
        String refreshToken = refreshTokenCookies.read(request, AUDIENCE)
                .orElseThrow(() -> new AuthException(AuthErrorType.INVALID_REFRESH_TOKEN));

        AuthResult<TokenResponseDto> result = authService.refresh(refreshToken, AUDIENCE);
        return ApiResponse.of(SuccessType.SUCCESS, result.body(),
                withCookie(refreshTokenCookies.create(AUDIENCE, result.refreshToken())));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<ApiResult<Void>> logout(HttpServletRequest request) {
        refreshTokenCookies.read(request, AUDIENCE).ifPresent(authService::logout);
        return ApiResponse.of(SuccessType.SUCCESS, null, withCookie(refreshTokenCookies.clear(AUDIENCE)));
    }

    private HttpHeaders withCookie(ResponseCookie cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
        return headers;
    }
}
