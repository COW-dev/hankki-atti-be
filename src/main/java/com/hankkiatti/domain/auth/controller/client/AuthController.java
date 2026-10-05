package com.hankkiatti.domain.auth.controller.client;

import com.hankkiatti.domain.auth.dto.request.LoginRequestDto;
import com.hankkiatti.domain.auth.dto.request.PasswordChangeRequestDto;
import com.hankkiatti.domain.auth.dto.response.LoginResponseDto;
import com.hankkiatti.domain.auth.dto.response.TokenResponseDto;
import com.hankkiatti.domain.auth.entity.TokenAudience;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.auth.service.AuthResult;
import com.hankkiatti.domain.auth.service.AuthService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import com.hankkiatti.global.security.AuthPrincipal;
import com.hankkiatti.global.security.RefreshTokenCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private static final TokenAudience AUDIENCE = TokenAudience.USER;

    private final AuthService authService;
    private final RefreshTokenCookies refreshTokenCookies;

    @Override
    @PostMapping("/login")
    public ResponseEntity<ApiResult<LoginResponseDto>> login(@Valid @RequestBody LoginRequestDto request) {
        AuthResult<LoginResponseDto> result = authService.loginUser(request);
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

    @Override
    @PatchMapping("/password")
    public ResponseEntity<ApiResult<TokenResponseDto>> changePassword(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PasswordChangeRequestDto request) {
        AuthResult<TokenResponseDto> result = authService.changePassword(principal.accountId(), request);
        return ApiResponse.of(SuccessType.SUCCESS, result.body(),
                withCookie(refreshTokenCookies.create(AUDIENCE, result.refreshToken())));
    }

    private HttpHeaders withCookie(ResponseCookie cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
        return headers;
    }
}
