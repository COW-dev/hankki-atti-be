package com.hankkiatti.domain.helper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.auth.repository.PasswordResetTokenRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.exception.HelperErrorType;
import com.hankkiatti.domain.helper.exception.HelperException;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.support.TestAccounts;
import com.hankkiatti.support.TestProfiles;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HelperAccountServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 11, 10, 0);
    private static final Long HELPER_ID = 7L;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private HelperRepository helperRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    private HelperAccountService service;
    private Account account;
    private Helper helper;

    @BeforeEach
    void setUp() {
        service = new HelperAccountService(accountRepository, helperRepository, passwordResetTokenRepository);
        account = TestAccounts.withId(HELPER_ID, AccountRole.HELPER, "hash", false);
        helper = TestProfiles.helper(account, "60230007");
    }

    @Test
    void deactivate_활성도우미_계정과도우미행을잠그고비활성화한다() {
        // given
        int tokenVersion = account.getTokenVersion();
        given(accountRepository.findByIdForUpdate(HELPER_ID)).willReturn(Optional.of(account));
        given(helperRepository.findByIdForUpdate(HELPER_ID)).willReturn(Optional.of(helper));

        // when
        boolean newly = service.deactivate(HELPER_ID, NOW);

        // then
        assertThat(newly).isTrue();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(account.getDeactivatedAt()).isEqualTo(NOW);
        assertThat(account.getTokenVersion()).isEqualTo(tokenVersion + 1);
        verify(passwordResetTokenRepository).invalidateAllByAccountId(HELPER_ID, NOW);
    }

    @Test
    void deactivate_이미비활성_바꾸지않고false() {
        // given
        account.deactivate(NOW.minusDays(1));
        given(accountRepository.findByIdForUpdate(HELPER_ID)).willReturn(Optional.of(account));
        given(helperRepository.findByIdForUpdate(HELPER_ID)).willReturn(Optional.of(helper));

        // when
        boolean newly = service.deactivate(HELPER_ID, NOW);

        // then
        assertThat(newly).isFalse();
        assertThat(account.getDeactivatedAt()).isEqualTo(NOW.minusDays(1));
        verify(passwordResetTokenRepository, never()).invalidateAllByAccountId(any(), any());
    }

    @Test
    void deactivate_도우미가아닌계정_NOT_FOUND() {
        // given
        given(accountRepository.findByIdForUpdate(HELPER_ID))
                .willReturn(Optional.of(TestAccounts.withId(HELPER_ID, AccountRole.STUDENT, "hash", false)));

        // when & then
        assertThatThrownBy(() -> service.deactivate(HELPER_ID, NOW))
                .isInstanceOf(HelperException.class)
                .extracting("errorCode").isEqualTo(HelperErrorType.NOT_FOUND);
    }

    @Test
    void activate_비활성도우미_다시활성() {
        // given
        account.deactivate(NOW.minusDays(1));
        given(accountRepository.findByIdForUpdate(HELPER_ID)).willReturn(Optional.of(account));

        // when
        Account activated = service.activate(HELPER_ID);

        // then
        assertThat(activated.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(activated.getDeactivatedAt()).isNull();
    }

    @Test
    void activate_이미활성_ALREADY_ACTIVE() {
        // given
        given(accountRepository.findByIdForUpdate(HELPER_ID)).willReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() -> service.activate(HELPER_ID))
                .isInstanceOf(HelperException.class)
                .extracting("errorCode").isEqualTo(HelperErrorType.ALREADY_ACTIVE);
    }
}
