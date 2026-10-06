package com.hankkiatti.domain.helper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.helper.dto.request.HelperSignupRequestDto;
import com.hankkiatti.domain.helper.dto.response.HelperSignupResponseDto;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.exception.HelperErrorType;
import com.hankkiatti.domain.helper.exception.HelperException;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HelperSignupServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 6, 12, 0);

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private HelperRepository helperRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private HelperSignupService helperSignupService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        helperSignupService = new HelperSignupService(accountRepository, helperRepository, passwordEncoder, clock);
    }

    private HelperSignupRequestDto request(String email, String studentNo, String phone, Boolean attiMember) {
        return new HelperSignupRequestDto(" 이도움 ", studentNo, email, phone, "hankki_helper", "hankki!2026",
                attiMember, true);
    }

    private void givenSavedAccountGetsId(long id) {
        given(accountRepository.saveAndFlush(any(Account.class))).willAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", id);
            return account;
        });
    }

    @Test
    void signup_정상입력_도우미계정과프로필저장() {
        // given
        given(passwordEncoder.encode("hankki!2026")).willReturn("hash");
        givenSavedAccountGetsId(7L);

        // when
        HelperSignupResponseDto result = helperSignupService.signup(
                request("New.Helper@MJU.ac.kr", "60230001", "010-1234-5678", null));

        // then
        ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(account.capture());
        assertThat(account.getValue().getLoginId()).isEqualTo("new.helper@mju.ac.kr");
        assertThat(account.getValue().getPasswordHash()).isEqualTo("hash");
        assertThat(account.getValue().getRole()).isEqualTo(AccountRole.HELPER);
        assertThat(account.getValue().isMustChangePassword()).isFalse();

        ArgumentCaptor<Helper> helper = ArgumentCaptor.forClass(Helper.class);
        verify(helperRepository).saveAndFlush(helper.capture());
        assertThat(helper.getValue().getName()).isEqualTo("이도움");
        assertThat(helper.getValue().getEmail()).isEqualTo("new.helper@mju.ac.kr");
        assertThat(helper.getValue().getStudentNo()).isEqualTo("60230001");
        assertThat(helper.getValue().isAttiMember()).isFalse();
        assertThat(helper.getValue().getGuideConfirmedAt()).isEqualTo(NOW);

        assertThat(result.accountId()).isEqualTo(7L);
        assertThat(result.loginId()).isEqualTo("new.helper@mju.ac.kr");
    }

    @Test
    void signup_하이픈없는전화번호_하이픈형식으로저장() {
        // given
        given(passwordEncoder.encode("hankki!2026")).willReturn("hash");
        givenSavedAccountGetsId(8L);

        // when
        helperSignupService.signup(request("helper@mju.ac.kr", "60230002", "0101234567", true));

        // then
        ArgumentCaptor<Helper> helper = ArgumentCaptor.forClass(Helper.class);
        verify(helperRepository).saveAndFlush(helper.capture());
        assertThat(helper.getValue().getPhone()).isEqualTo("010-123-4567");
        assertThat(helper.getValue().isAttiMember()).isTrue();
    }

    @Test
    void signup_이미가입된이메일_DUPLICATE_EMAIL() {
        // given
        given(accountRepository.existsByLoginId("helper@mju.ac.kr")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> helperSignupService.signup(
                request("Helper@mju.ac.kr", "60230001", "010-1234-5678", false)))
                .isInstanceOf(HelperException.class)
                .extracting("errorCode").isEqualTo(HelperErrorType.DUPLICATE_EMAIL);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void signup_이미가입된학번_DUPLICATE_STUDENT_NO() {
        // given
        given(helperRepository.existsByStudentNo("60230001")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> helperSignupService.signup(
                request("helper@mju.ac.kr", "60230001", "010-1234-5678", false)))
                .isInstanceOf(HelperException.class)
                .extracting("errorCode").isEqualTo(HelperErrorType.DUPLICATE_STUDENT_NO);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void signup_같은값으로동시에가입되어유니크제약위반_SIGNUP_CONFLICT() {
        // given — 중복 확인은 둘 다 통과했지만 먼저 들어간 가입이 DB에 저장된 상태
        given(passwordEncoder.encode("hankki!2026")).willReturn("hash");
        given(accountRepository.saveAndFlush(any(Account.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate entry for key 'login_id'"));

        // when & then
        assertThatThrownBy(() -> helperSignupService.signup(
                request("helper@mju.ac.kr", "60230001", "010-1234-5678", false)))
                .isInstanceOf(HelperException.class)
                .extracting("errorCode").isEqualTo(HelperErrorType.SIGNUP_CONFLICT);
    }
}
