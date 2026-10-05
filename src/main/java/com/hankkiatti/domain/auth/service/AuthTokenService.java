package com.hankkiatti.domain.auth.service;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.auth.entity.RefreshToken;
import com.hankkiatti.domain.auth.entity.TokenAudience;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.auth.repository.RefreshTokenRepository;
import com.hankkiatti.global.security.AccessToken;
import com.hankkiatti.global.security.AuthProperties;
import com.hankkiatti.global.security.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * access·refresh 토큰 발급과 refresh 토큰 회전·폐기.
 */
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthProperties authProperties;

    @Transactional
    public IssuedTokens issue(Account account, TokenAudience audience, LocalDateTime now) {
        AccessToken accessToken = jwtTokenProvider.issue(account, audience);
        String refreshToken = newRefreshToken();
        LocalDateTime expiresAt = now.plus(authProperties.jwt().refreshTokenTtl());

        refreshTokenRepository.save(
                new RefreshToken(account, hash(refreshToken), audience, account.getTokenVersion(), expiresAt));
        return new IssuedTokens(accessToken.value(), accessToken.expiresInSeconds(), refreshToken);
    }

    /**
     * refresh 토큰을 새 토큰 한 쌍으로 바꾼다. 쓴 토큰은 폐기한다.
     * 이미 폐기된 토큰이 다시 오면 탈취로 보고 그 계정의 refresh 토큰을 모두 폐기한다 — 예외가 나도 이 폐기는 커밋한다.
     */
    @Transactional(noRollbackFor = AuthException.class)
    public IssuedTokens rotate(String rawRefreshToken, TokenAudience audience, LocalDateTime now) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> new AuthException(AuthErrorType.INVALID_REFRESH_TOKEN));

        Account account = token.getAccount();
        if (token.isRevoked()) {
            refreshTokenRepository.revokeAllByAccountId(account.getId(), now);
            throw new AuthException(AuthErrorType.INVALID_REFRESH_TOKEN, "재사용된 refresh 토큰, accountId=" + account.getId());
        }
        if (token.isExpired(now)
                || token.getAudience() != audience
                || !account.isActive()
                || token.getTokenVersion() != account.getTokenVersion()
                || !audience.allows(account.getRole())) {
            throw new AuthException(AuthErrorType.INVALID_REFRESH_TOKEN, "accountId=" + account.getId());
        }

        token.revoke(now);
        return issue(account, audience, now);
    }

    @Transactional
    public void revoke(String rawRefreshToken, LocalDateTime now) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .ifPresent(token -> token.revoke(now));
    }

    @Transactional
    public void revokeAll(Long accountId, LocalDateTime now) {
        refreshTokenRepository.revokeAllByAccountId(accountId, now);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // 모든 JVM이 SHA-256을 제공해야 하므로 일어나지 않는다
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    private static String newRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
