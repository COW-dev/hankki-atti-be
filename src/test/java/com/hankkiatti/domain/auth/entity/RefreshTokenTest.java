package com.hankkiatti.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.support.TestAccounts;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 12, 0);

    private RefreshToken newToken(LocalDateTime expiresAt) {
        return new RefreshToken(TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false),
                "token-hash", TokenAudience.USER, 0, expiresAt);
    }

    @Test
    void isExpired_만료시각과같음_만료() {
        // given
        RefreshToken token = newToken(NOW);

        // when & then
        assertThat(token.isExpired(NOW)).isTrue();
        assertThat(token.isExpired(NOW.minusSeconds(1))).isFalse();
    }

    @Test
    void revoke_두번호출_처음폐기시각유지() {
        // given
        RefreshToken token = newToken(NOW.plusDays(14));

        // when
        token.revoke(NOW);
        token.revoke(NOW.plusHours(1));

        // then
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(NOW);
    }
}
