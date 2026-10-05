package com.hankkiatti.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.auth.entity.RefreshToken;
import com.hankkiatti.domain.auth.entity.TokenAudience;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.auth.repository.RefreshTokenRepository;
import com.hankkiatti.global.security.AccessToken;
import com.hankkiatti.global.security.AuthProperties;
import com.hankkiatti.global.security.JwtTokenProvider;
import com.hankkiatti.support.TestAccounts;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 12, 0);
    private static final String RAW_TOKEN = "raw-refresh-token";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthTokenService authTokenService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
                new AuthProperties.Jwt(null, "hankki-atti", Duration.ofMinutes(30), Duration.ofDays(14)),
                new AuthProperties.RefreshCookie(true),
                new AuthProperties.Cors(List.of("http://localhost:3000")));
        authTokenService = new AuthTokenService(refreshTokenRepository, jwtTokenProvider, properties);
    }

    private Account student() {
        return TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false);
    }

    private RefreshToken storedToken(Account account, TokenAudience audience, int version, LocalDateTime expiresAt) {
        return new RefreshToken(account, AuthTokenService.hash(RAW_TOKEN), audience, version, expiresAt);
    }

    @Test
    void issue_토큰발급_원문대신해시를저장하고14일뒤만료() {
        // given
        Account account = student();
        given(jwtTokenProvider.issue(account, TokenAudience.USER)).willReturn(new AccessToken("access", 1800));

        // when
        IssuedTokens tokens = authTokenService.issue(account, TokenAudience.USER, NOW);

        // then
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(tokens.accessToken()).isEqualTo("access");
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(AuthTokenService.hash(tokens.refreshToken()))
                .isNotEqualTo(tokens.refreshToken());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusDays(14));
    }

    @Test
    void rotate_유효한토큰_기존토큰폐기하고새토큰발급() {
        // given
        Account account = student();
        RefreshToken token = storedToken(account, TokenAudience.USER, 0, NOW.plusDays(1));
        given(refreshTokenRepository.findByTokenHash(AuthTokenService.hash(RAW_TOKEN))).willReturn(Optional.of(token));
        given(jwtTokenProvider.issue(account, TokenAudience.USER)).willReturn(new AccessToken("new-access", 1800));

        // when
        IssuedTokens tokens = authTokenService.rotate(RAW_TOKEN, TokenAudience.USER, NOW);

        // then
        assertThat(token.isRevoked()).isTrue();
        assertThat(tokens.accessToken()).isEqualTo("new-access");
        assertThat(tokens.refreshToken()).isNotEqualTo(RAW_TOKEN);
    }

    @Test
    void rotate_없는토큰_예외() {
        // given
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authTokenService.rotate(RAW_TOKEN, TokenAudience.USER, NOW))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.INVALID_REFRESH_TOKEN);
    }

    @Test
    void rotate_폐기된토큰재사용_계정토큰전부폐기하고예외() {
        // given
        Account account = student();
        RefreshToken token = storedToken(account, TokenAudience.USER, 0, NOW.plusDays(1));
        token.revoke(NOW.minusMinutes(5));
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(token));

        // when & then
        assertThatThrownBy(() -> authTokenService.rotate(RAW_TOKEN, TokenAudience.USER, NOW))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.INVALID_REFRESH_TOKEN);
        verify(refreshTokenRepository).revokeAllByAccountId(1L, NOW);
        verify(jwtTokenProvider, never()).issue(any(), any());
    }

    @Test
    void rotate_만료된토큰_예외() {
        // given
        RefreshToken token = storedToken(student(), TokenAudience.USER, 0, NOW);
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(token));

        // when & then
        assertThatThrownBy(() -> authTokenService.rotate(RAW_TOKEN, TokenAudience.USER, NOW))
                .isInstanceOf(AuthException.class);
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    void rotate_비밀번호변경으로버전이바뀜_예외() {
        // given
        Account account = student();
        RefreshToken token = storedToken(account, TokenAudience.USER, 0, NOW.plusDays(1));
        account.changePassword("new-hash");
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(token));

        // when & then
        assertThatThrownBy(() -> authTokenService.rotate(RAW_TOKEN, TokenAudience.USER, NOW))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void rotate_다른앱쿠키로요청_예외() {
        // given
        RefreshToken token = storedToken(student(), TokenAudience.USER, 0, NOW.plusDays(1));
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(token));

        // when & then
        assertThatThrownBy(() -> authTokenService.rotate(RAW_TOKEN, TokenAudience.ADMIN, NOW))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void rotate_비활성계정_예외() {
        // given
        Account account = student();
        RefreshToken token = storedToken(account, TokenAudience.USER, 0, NOW.plusDays(1));
        account.deactivate(NOW.minusHours(1));
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(token));

        // when & then
        assertThatThrownBy(() -> authTokenService.rotate(RAW_TOKEN, TokenAudience.USER, NOW))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void revoke_있는토큰_폐기() {
        // given
        RefreshToken token = storedToken(student(), TokenAudience.USER, 0, NOW.plusDays(1));
        given(refreshTokenRepository.findByTokenHash(AuthTokenService.hash(RAW_TOKEN))).willReturn(Optional.of(token));

        // when
        authTokenService.revoke(RAW_TOKEN, NOW);

        // then
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    void revokeAll_계정의토큰_일괄폐기위임() {
        // when
        authTokenService.revokeAll(7L, NOW);

        // then
        verify(refreshTokenRepository).revokeAllByAccountId(7L, NOW);
    }
}
