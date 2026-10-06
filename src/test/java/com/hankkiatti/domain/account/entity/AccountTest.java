package com.hankkiatti.domain.account.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hankkiatti.domain.account.exception.AccountErrorType;
import com.hankkiatti.domain.account.exception.AccountException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 12, 0);

    private Account newAccount(boolean mustChangePassword) {
        return new Account("student01", "hash", AccountRole.STUDENT, mustChangePassword, false);
    }

    @Test
    void changePassword_변경필요상태_해제되고토큰버전증가() {
        // given
        Account account = newAccount(true);

        // when
        account.changePassword("newHash");

        // then
        assertThat(account.getPasswordHash()).isEqualTo("newHash");
        assertThat(account.isMustChangePassword()).isFalse();
        assertThat(account.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void issueTemporaryPassword_변경필요상태로전환() {
        // given
        Account account = newAccount(false);

        // when
        account.issueTemporaryPassword("tempHash");

        // then
        assertThat(account.getPasswordHash()).isEqualTo("tempHash");
        assertThat(account.isMustChangePassword()).isTrue();
        assertThat(account.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void deactivate_활성계정_비활성화되고토큰버전증가() {
        // given
        Account account = newAccount(false);

        // when
        account.deactivate(NOW);

        // then
        assertThat(account.isActive()).isFalse();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(account.getDeactivatedAt()).isEqualTo(NOW);
        assertThat(account.getTokenVersion()).isEqualTo(1);
    }

    @Test
    void deactivate_이미비활성_예외() {
        // given
        Account account = newAccount(false);
        account.deactivate(NOW);

        // when & then
        assertThatThrownBy(() -> account.deactivate(NOW))
                .isInstanceOf(AccountException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorType.ALREADY_INACTIVE);
    }

    @Test
    void activate_비활성계정_활성화되고비활성시각초기화() {
        // given
        Account account = newAccount(false);
        account.deactivate(NOW);

        // when
        account.activate();

        // then
        assertThat(account.isActive()).isTrue();
        assertThat(account.getDeactivatedAt()).isNull();
    }

    @Test
    void activate_이미활성_예외() {
        // given
        Account account = newAccount(false);

        // when & then
        assertThatThrownBy(account::activate)
                .isInstanceOf(AccountException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorType.ALREADY_ACTIVE);
    }

    @Test
    void normalizeLoginId_이메일_앞뒤공백제거하고소문자로() {
        assertThat(Account.normalizeLoginId("  New.Helper@MJU.ac.kr ")).isEqualTo("new.helper@mju.ac.kr");
    }

    @Test
    void normalizeLoginId_학번_앞뒤공백만제거() {
        assertThat(Account.normalizeLoginId(" 60231234 ")).isEqualTo("60231234");
    }
}
