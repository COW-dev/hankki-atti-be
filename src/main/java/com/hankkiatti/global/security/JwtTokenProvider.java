package com.hankkiatti.global.security;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.auth.entity.TokenAudience;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * access 토큰(JWT)을 발급한다. 검증은 Spring Security 리소스 서버(JwtDecoder + AccountJwtAuthenticationConverter)가 한다.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    public static final String VERSION_CLAIM = "ver";
    public static final String ROLE_CLAIM = "role";

    private final JwtEncoder jwtEncoder;
    private final AuthProperties authProperties;
    private final Clock clock;

    public AccessToken issue(Account account, TokenAudience audience) {
        Duration ttl = authProperties.jwt().accessTokenTtl();
        Instant now = clock.instant();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.jwt().issuer())
                .subject(String.valueOf(account.getId()))
                .audience(List.of(audience.getClaimValue()))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(ROLE_CLAIM, account.getRole().name())
                .claim(VERSION_CLAIM, account.getTokenVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, ttl.toSeconds());
    }
}
