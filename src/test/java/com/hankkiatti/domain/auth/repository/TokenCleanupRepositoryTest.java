package com.hankkiatti.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.auth.entity.PasswordResetToken;
import com.hankkiatti.domain.auth.entity.RefreshToken;
import com.hankkiatti.domain.auth.entity.TokenAudience;
import com.hankkiatti.global.config.JpaConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * 만료 토큰 정리 쿼리가 만료된 행만 지우는지 실제 DB(H2)로 확인한다.
 */
@DataJpaTest
// 기본 내장 H2로 바꾸면 enum CHECK 제약 평가 중 "database has been closed"로 INSERT가 실패한다. 테스트 설정의 H2(MySQL 모드)를 그대로 쓴다
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class TokenCleanupRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 4, 30);

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    private Account account;

    @BeforeEach
    void setUp() {
        account = accountRepository.save(new Account("60230001", "hash", AccountRole.HELPER, false, false));
    }

    private RefreshToken saveRefresh(String hash, LocalDateTime expiresAt, boolean revoked) {
        RefreshToken token = new RefreshToken(account, hash, TokenAudience.USER, 0, expiresAt);
        if (revoked) {
            token.revoke(expiresAt.minusDays(1));
        }
        return refreshTokenRepository.save(token);
    }

    @Test
    void deleteExpired_refresh_만료된행만지우고폐기됐지만만료전인행은남김() {
        // given
        RefreshToken expired = saveRefresh("expired", NOW.minusMinutes(1), false);
        RefreshToken expiredNow = saveRefresh("expired-now", NOW, false);
        RefreshToken revokedButValid = saveRefresh("revoked", NOW.plusDays(1), true);
        RefreshToken valid = saveRefresh("valid", NOW.plusDays(14), false);

        // when
        int deleted = refreshTokenRepository.deleteExpired(NOW);

        // then
        assertThat(deleted).isEqualTo(2);
        assertThat(refreshTokenRepository.findById(expired.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(expiredNow.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(revokedButValid.getId())).isPresent();
        assertThat(refreshTokenRepository.findById(valid.getId())).isPresent();
    }

    @Test
    void deleteExpired_재설정토큰_만료된행만지움() {
        // given — 유효 시간 30분: 31분 전 발급은 만료, 10분 전 발급은 사용했어도 아직 유효 기간 안
        PasswordResetToken expired = passwordResetTokenRepository.save(
                new PasswordResetToken(account, "reset-expired", NOW.minusMinutes(31)));
        PasswordResetToken usedButNotExpired = passwordResetTokenRepository.save(
                new PasswordResetToken(account, "reset-used", NOW.minusMinutes(10)));
        usedButNotExpired.markUsed(NOW.minusMinutes(5));
        passwordResetTokenRepository.save(usedButNotExpired);

        // when
        int deleted = passwordResetTokenRepository.deleteExpired(NOW);

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(passwordResetTokenRepository.findById(expired.getId())).isEmpty();
        assertThat(passwordResetTokenRepository.findById(usedButNotExpired.getId())).isPresent();
    }

    @Test
    void deleteExpired_만료된행없음_0건() {
        // given
        saveRefresh("valid", NOW.plusDays(14), false);

        // when
        int deleted = refreshTokenRepository.deleteExpired(NOW);

        // then
        assertThat(deleted).isZero();
    }
}
