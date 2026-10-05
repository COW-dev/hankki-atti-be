package com.hankkiatti.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.auth.entity.TokenAudience;
import com.hankkiatti.global.config.JwtConfig;
import com.hankkiatti.support.TestAccounts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtTokenProviderTest {

    private static final String SECRET = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final JwtConfig jwtConfig = new JwtConfig();
    private AuthProperties properties;
    private JwtTokenProvider provider;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        properties = propertiesWithSecret(SECRET);
        SecretKey key = jwtConfig.jwtSecretKey(properties);
        provider = new JwtTokenProvider(jwtConfig.jwtEncoder(key), properties, Clock.systemUTC());
        decoder = jwtConfig.jwtDecoder(key, properties);
    }

    private AuthProperties propertiesWithSecret(String secret) {
        return new AuthProperties(
                new AuthProperties.Jwt(secret, "hankki-atti", Duration.ofMinutes(30), Duration.ofDays(14)),
                new AuthProperties.RefreshCookie(true),
                new AuthProperties.Cors(List.of("http://localhost:3000")));
    }

    @Test
    void issue_발급한토큰_디코더로검증되고계정정보를담음() {
        // given
        Account account = TestAccounts.withId(5L, AccountRole.HELPER, "hash", false);
        account.changePassword("new-hash");

        // when
        AccessToken token = provider.issue(account, TokenAudience.USER);
        Jwt jwt = decoder.decode(token.value());

        // then
        assertThat(token.expiresInSeconds()).isEqualTo(1800);
        assertThat(jwt.getSubject()).isEqualTo("5");
        assertThat(jwt.getAudience()).containsExactly("user");
        assertThat(jwt.<Number>getClaim(JwtTokenProvider.VERSION_CLAIM).intValue()).isEqualTo(1);
        assertThat(jwt.<String>getClaim(JwtTokenProvider.ROLE_CLAIM)).isEqualTo("HELPER");
    }

    @Test
    void decode_다른키로서명된토큰_예외() throws Exception {
        // given
        AuthProperties other = propertiesWithSecret(
                Base64.getEncoder().encodeToString("ffffffffffffffffffffffffffffffff".getBytes()));
        SecretKey otherKey = jwtConfig.jwtSecretKey(other);
        JwtTokenProvider otherProvider = new JwtTokenProvider(jwtConfig.jwtEncoder(otherKey), other, Clock.systemUTC());
        String token = otherProvider.issue(TestAccounts.withId(1L, AccountRole.STUDENT, "h", false), TokenAudience.USER)
                .value();

        // when & then
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void decode_만료된토큰_예외() throws Exception {
        // given
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneId.of("Asia/Seoul"));
        SecretKey key = jwtConfig.jwtSecretKey(properties);
        JwtTokenProvider pastProvider = new JwtTokenProvider(jwtConfig.jwtEncoder(key), properties, past);
        String token = pastProvider.issue(TestAccounts.withId(1L, AccountRole.STUDENT, "h", false), TokenAudience.USER)
                .value();

        // when & then
        assertThatThrownBy(() -> jwtConfig.jwtDecoder(key, properties).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void jwtSecretKey_32바이트미만_기동실패() {
        // given
        AuthProperties shortSecret = propertiesWithSecret(Base64.getEncoder().encodeToString("short".getBytes()));

        // when & then
        assertThatThrownBy(() -> jwtConfig.jwtSecretKey(shortSecret)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jwtSecretKey_비어있음_임의키생성() throws Exception {
        // when
        SecretKey key = jwtConfig.jwtSecretKey(propertiesWithSecret(""));

        // then
        assertThat(key.getEncoded()).hasSizeGreaterThanOrEqualTo(32);
    }
}
