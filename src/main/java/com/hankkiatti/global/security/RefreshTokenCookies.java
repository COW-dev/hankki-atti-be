package com.hankkiatti.global.security;

import com.hankkiatti.domain.auth.entity.TokenAudience;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * refresh 토큰 쿠키. 사용자 앱과 관리자 페이지가 같은 API 호스트를 쓰므로 쿠키 이름을 나눈다.
 * Domain을 지정하지 않아 API 호스트 전용이고, Secure일 때 __Host- 접두사로 다른 서브도메인이 덮어쓰지 못하게 한다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookies {

    private static final String HOST_PREFIX = "__Host-";
    private static final String USER_COOKIE = "hankki_rt";
    private static final String ADMIN_COOKIE = "hankki_admin_rt";

    private final AuthProperties authProperties;

    public String name(TokenAudience audience) {
        String base = audience == TokenAudience.ADMIN ? ADMIN_COOKIE : USER_COOKIE;
        return isSecure() ? HOST_PREFIX + base : base;
    }

    public ResponseCookie create(TokenAudience audience, String refreshToken) {
        return build(audience, refreshToken, authProperties.jwt().refreshTokenTtl());
    }

    public ResponseCookie clear(TokenAudience audience) {
        return build(audience, "", Duration.ZERO);
    }

    public Optional<String> read(HttpServletRequest request, TokenAudience audience) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        String name = name(audience);
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie build(TokenAudience audience, String value, Duration maxAge) {
        return ResponseCookie.from(name(audience), value)
                .httpOnly(true)
                .secure(isSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private boolean isSecure() {
        return authProperties.refreshCookie().secure();
    }
}
