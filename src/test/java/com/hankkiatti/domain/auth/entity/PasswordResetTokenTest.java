package com.hankkiatti.domain.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.support.TestAccounts;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PasswordResetTokenTest {

    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 10, 8, 12, 0);

    private PasswordResetToken newToken() {
        return new PasswordResetToken(TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false),
                "token-hash", ISSUED_AT);
    }

    @Test
    void isUsable_발급후30분정각_만료() {
        // given
        PasswordResetToken token = newToken();

        // when & then
        assertThat(token.isUsable(ISSUED_AT.plusMinutes(30).minusSeconds(1))).isTrue();
        assertThat(token.isUsable(ISSUED_AT.plusMinutes(30))).isFalse();
    }

    @Test
    void markUsed_사용하면_더못쓰고처음사용시각유지() {
        // given
        PasswordResetToken token = newToken();

        // when
        token.markUsed(ISSUED_AT.plusMinutes(5));
        token.markUsed(ISSUED_AT.plusMinutes(10));

        // then
        assertThat(token.isUsable(ISSUED_AT.plusMinutes(6))).isFalse();
        assertThat(token.getUsedAt()).isEqualTo(ISSUED_AT.plusMinutes(5));
    }

    @Test
    void issuedWithin_10분경계_정각부터는지난것으로봄() {
        // given
        PasswordResetToken token = newToken();
        Duration tenMinutes = Duration.ofMinutes(10);

        // when & then
        assertThat(token.issuedWithin(tenMinutes, ISSUED_AT.plusMinutes(10).minusSeconds(1))).isTrue();
        assertThat(token.issuedWithin(tenMinutes, ISSUED_AT.plusMinutes(10))).isFalse();
    }
}
