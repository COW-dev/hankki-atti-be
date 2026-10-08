package com.hankkiatti.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.dto.request.MeContactUpdateRequestDto;
import com.hankkiatti.domain.account.dto.request.MeSettingsUpdateRequestDto;
import com.hankkiatti.domain.account.dto.response.MeResponseDto;
import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.exception.AccountErrorType;
import com.hankkiatti.domain.account.exception.AccountException;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestAccounts;
import com.hankkiatti.support.TestProfiles;
import java.math.BigDecimal;
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

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private HelperRepository helperRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @InjectMocks
    private AccountService accountService;

    private Account givenStudent(Long id) {
        Account account = TestAccounts.withId(id, AccountRole.STUDENT, "hash", false);
        given(accountRepository.findById(id)).willReturn(Optional.of(account));
        given(studentRepository.findById(id)).willReturn(Optional.of(TestProfiles.student(account)));
        return account;
    }

    private Helper givenHelper(Long id, BigDecimal volunteerHoursSum) {
        Account account = TestAccounts.withId(id, AccountRole.HELPER, "hash", false);
        Helper helper = TestProfiles.helper(account, "60230001");
        given(accountRepository.findById(id)).willReturn(Optional.of(account));
        given(helperRepository.findById(id)).willReturn(Optional.of(helper));
        given(applicationRepository.sumVolunteerHoursByHelperId(id)).willReturn(volunteerHoursSum);
        return helper;
    }

    @Test
    void getMe_장애학생_프로필포함하고도우미정보없음() {
        // given
        givenStudent(1L);

        // when
        MeResponseDto result = accountService.getMe(1L);

        // then
        assertThat(result.role()).isEqualTo(AccountRole.STUDENT);
        assertThat(result.name()).isEqualTo("김한끼");
        assertThat(result.phone()).isEqualTo("010-0000-0000");
        assertThat(result.kakaoId()).isEqualTo("kakao_student");
        assertThat(result.helper()).isNull();
        verify(applicationRepository, never()).sumVolunteerHoursByHelperId(anyLong());
    }

    @Test
    void getMe_도우미_아띠소속과봉사시간합계() {
        // given
        givenHelper(2L, new BigDecimal("3.0"));

        // when
        MeResponseDto result = accountService.getMe(2L);

        // then
        assertThat(result.studentNo()).isEqualTo("60230001");
        assertThat(result.helper().attiMember()).isTrue();
        assertThat(result.helper().volunteerHours()).isEqualByComparingTo("3.0");
    }

    @Test
    void getMe_봉사기록없는도우미_0점0시간() {
        // given
        givenHelper(2L, null);

        // when
        MeResponseDto result = accountService.getMe(2L);

        // then
        assertThat(result.helper().volunteerHours()).isEqualTo(new BigDecimal("0.0"));
    }

    @Test
    void getMe_프로필없는계정_NOT_FOUND() {
        // given
        Account account = TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false);
        given(accountRepository.findById(1L)).willReturn(Optional.of(account));
        given(studentRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> accountService.getMe(1L))
                .isInstanceOf(AccountException.class)
                .extracting("errorCode").isEqualTo(AccountErrorType.NOT_FOUND);
    }

    @Test
    void updateContact_도우미_하이픈형식으로저장하고응답반영() {
        // given
        Helper helper = givenHelper(2L, null);

        // when
        MeResponseDto result = accountService.updateContact(2L, new MeContactUpdateRequestDto("01098765432", "new_kakao"));

        // then
        assertThat(helper.getPhone()).isEqualTo("010-9876-5432");
        assertThat(helper.getKakaoId()).isEqualTo("new_kakao");
        assertThat(result.phone()).isEqualTo("010-9876-5432");
        assertThat(result.kakaoId()).isEqualTo("new_kakao");
    }

    @Test
    void updateContact_장애학생_ACCESS_DENIED() {
        // given
        Account account = TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false);
        given(accountRepository.findById(1L)).willReturn(Optional.of(account));

        // when & then
        assertThatThrownBy(() ->
                accountService.updateContact(1L, new MeContactUpdateRequestDto("010-9876-5432", "new_kakao")))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
        verify(helperRepository, never()).findById(anyLong());
    }

    @Test
    void updateSettings_접근성모드켜기_계정에저장되고응답에반영() {
        // given
        Account student = givenStudent(1L);

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
        Account helper = givenHelper(2L, null).getAccount();
        helper.changeAccessibilityMode(true);

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
