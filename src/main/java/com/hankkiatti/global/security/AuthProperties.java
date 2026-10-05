package com.hankkiatti.global.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 인증 설정. 값이 없으면 기본값을 쓴다 (secret이 비면 임의 키 — JwtConfig 참고).
 */
@ConfigurationProperties("auth")
public record AuthProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue RefreshCookie refreshCookie,
        @DefaultValue Cors cors
) {

    public record Jwt(
            // Base64 인코딩된 32바이트 이상의 HS256 키
            String secret,
            @DefaultValue("hankki-atti") String issuer,
            @DefaultValue("30m") Duration accessTokenTtl,
            @DefaultValue("14d") Duration refreshTokenTtl
    ) {}

    public record RefreshCookie(
            // true면 Secure + __Host- 접두사. 로컬 http 개발에서만 false
            @DefaultValue("true") boolean secure
    ) {}

    public record Cors(
            // 정확한 출처만 쓴다. *.bluerack.org 같은 와일드카드 금지 (공유 도메인)
            @DefaultValue("http://localhost:3000") List<String> allowedOrigins
    ) {}
}
