package com.hankkiatti.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.hankkiatti.domain.account.dto.request.MeSettingsUpdateRequestDto;
import com.hankkiatti.domain.account.dto.response.MeResponseDto;
import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.exception.AccountErrorType;
import com.hankkiatti.domain.account.exception.AccountException;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.support.TestAccounts;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void updateSettings_접근성모드켜기_계정에저장되고응답에반영() {
        // given
        Account student = TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false);
        given(accountRepository.findById(1L)).willReturn(Optional.of(student));

        // when
        MeResponseDto result = accountService.updateSettings(1L, new MeSettingsUpdateRequestDto(true));

        // then
        assertThat(student.isAccessibilityMode()).isTrue();
        assertThat(result.accessibilityMode()).isTrue();
        assertThat(result.role()).isEqualTo(AccountRole.STUDENT);
    }

    @Test
    void updateSettings_켜진상태에서끄기_꺼짐으로저장() {
        // given
        Account helper = TestAccounts.withId(2L, AccountRole.HELPER, "hash", false);
        helper.changeAccessibilityMode(true);
        given(accountRepository.findById(2L)).willReturn(Optional.of(helper));

        // when
        MeResponseDto result = accountService.updateSettings(2L, new MeSettingsUpdateRequestDto(false));

        // then
        assertThat(helper.isAccessibilityMode()).isFalse();
        assertThat(result.accessibilityMode()).isFalse();
    }

    @Test
    void updateSettings_없는계정_NOT_FOUND() {
        // given
        given(accountRepository.findById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> accountService.updateSettings(99L, new MeSettingsUpdateRequestDto(true)))
                .isInstanceOf(AccountException.class)
                .extracting("errorCode").isEqualTo(AccountErrorType.NOT_FOUND);
    }
}
