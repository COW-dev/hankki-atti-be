package com.hankkiatti.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.auth.dto.request.PasswordResetConfirmRequestDto;
import com.hankkiatti.domain.auth.dto.request.PasswordResetRequestDto;
import com.hankkiatti.domain.auth.dto.request.PasswordResetVerifyRequestDto;
import com.hankkiatti.domain.auth.entity.PasswordResetToken;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.auth.repository.PasswordResetTokenRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.service.MailOutboxService;
import com.hankkiatti.domain.mail.service.MailProperties;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestAccounts;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0);
    private static final String LINK_PREFIX = "https://app.example.org/password-reset#token=";
    private static final String RAW_TOKEN = "raw-token";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private HelperRepository helperRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private MailOutboxService mailOutboxService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        // 끝의 /는 링크를 만들 때 지운다
        MailProperties mailProperties = new MailProperties("no-reply@test", "한끼아띠", "https://app.example.org/",
                new MailProperties.Outbox(20, List.of(Duration.ofMinutes(1)), Duration.ofMinutes(5)));
        passwordResetService = new PasswordResetService(accountRepository, studentRepository, helperRepository,
                passwordResetTokenRepository, authTokenService, mailOutboxService, mailProperties, passwordEncoder,
                clock);
    }

    private Account student(Long id) {
        Account account = TestAccounts.withId(id, AccountRole.STUDENT, "old-hash", false);
        given(accountRepository.findByLoginIdForUpdate("60231234")).willReturn(Optional.of(account));
        return account;
    }

    private void givenStudentProfile(Account account) {
        given(studentRepository.findById(account.getId())).willReturn(Optional.of(new Student(account, "김학생",
                "60231234", "010-0000-0000", "kakao", "60231234@mju.ac.kr", DisabilityType.PHYSICAL, null)));
    }

    private PasswordResetToken tokenIssuedAt(Account account, LocalDateTime issuedAt) {
        return new PasswordResetToken(account, AuthTokenService.hash(RAW_TOKEN), issuedAt);
    }

    private String sentMailBody(String recipient, Long accountId) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailOutboxService).enqueue(eq(MailType.PASSWORD_RESET), eq(recipient),
                eq(PasswordResetService.MAIL_SUBJECT), body.capture(), eq(accountId));
        return body.getValue();
    }

    private String tokenInLink(String mailBody) {
        int start = mailBody.indexOf(LINK_PREFIX) + LINK_PREFIX.length();
        return mailBody.substring(start, mailBody.indexOf('\n', start));
    }

    private void assertLinkInvalid(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.PASSWORD_RESET_LINK_INVALID);
    }

    @Test
    void request_학생_이전링크무효화후학교이메일로새링크발송() {
        // given
        Account account = student(1L);
        givenStudentProfile(account);
        given(passwordResetTokenRepository.findTopByAccountIdOrderByIdDesc(1L)).willReturn(Optional.empty());

        // when
        passwordResetService.request(new PasswordResetRequestDto(" 60231234 "));

        // then
        verify(passwordResetTokenRepository).invalidateAllByAccountId(1L, NOW);
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(saved.capture());
        String body = sentMailBody("60231234@mju.ac.kr", 1L);
        assertThat(body).contains(LINK_PREFIX);
        // DB에는 메일 링크 토큰의 해시만 저장한다
        String rawToken = tokenInLink(body);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(AuthTokenService.hash(rawToken)).isNotEqualTo(rawToken);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusMinutes(30));
    }

    @Test
    void request_도우미_가입이메일로발송() {
        // given
        Account account = TestAccounts.withId(2L, AccountRole.HELPER, "old-hash", false);
        given(accountRepository.findByLoginIdForUpdate("helper@mju.ac.kr")).willReturn(Optional.of(account));
        given(passwordResetTokenRepository.findTopByAccountIdOrderByIdDesc(2L)).willReturn(Optional.empty());
        given(helperRepository.findById(2L)).willReturn(Optional.of(new Helper(account, "이도움", "60230001",
                "helper@mju.ac.kr", "010-1111-1111", "kakao", true, NOW.minusDays(1))));

        // when
        passwordResetService.request(new PasswordResetRequestDto("Helper@MJU.ac.kr"));

        // then
        assertThat(sentMailBody("helper@mju.ac.kr", 2L)).contains(LINK_PREFIX);
    }

    @Test
    void request_없는아이디_발송하지않고예외도없음() {
        // given
        given(accountRepository.findByLoginIdForUpdate("99999999")).willReturn(Optional.empty());

        // when
        passwordResetService.request(new PasswordResetRequestDto("99999999"));

        // then
        verify(passwordResetTokenRepository, never()).save(any());
        verify(mailOutboxService, never()).enqueue(any(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void request_관리자계정_발송하지않음() {
        // given
        Account admin = TestAccounts.withId(3L, AccountRole.ADMIN, "hash", false);
        given(accountRepository.findByLoginIdForUpdate("center01")).willReturn(Optional.of(admin));

        // when
        passwordResetService.request(new PasswordResetRequestDto("center01"));

        // then
        verify(mailOutboxService, never()).enqueue(any(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void request_사용중지계정_발송하지않음() {
        // given
        Account account = student(1L);
        account.deactivate(NOW.minusDays(1));

        // when
        passwordResetService.request(new PasswordResetRequestDto("60231234"));

        // then
        verify(mailOutboxService, never()).enqueue(any(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void request_10분이내재요청_발송하지않고이전링크유지() {
        // given
        Account account = student(1L);
        given(passwordResetTokenRepository.findTopByAccountIdOrderByIdDesc(1L))
                .willReturn(Optional.of(tokenIssuedAt(account, NOW.minusMinutes(9))));

        // when
        passwordResetService.request(new PasswordResetRequestDto("60231234"));

        // then
        verify(passwordResetTokenRepository, never()).invalidateAllByAccountId(anyLong(), any());
        verify(mailOutboxService, never()).enqueue(any(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void request_10분지난재요청_새링크발송() {
        // given
        Account account = student(1L);
        givenStudentProfile(account);
        given(passwordResetTokenRepository.findTopByAccountIdOrderByIdDesc(1L))
                .willReturn(Optional.of(tokenIssuedAt(account, NOW.minusMinutes(10))));

        // when
        passwordResetService.request(new PasswordResetRequestDto("60231234"));

        // then
        verify(passwordResetTokenRepository).invalidateAllByAccountId(1L, NOW);
        assertThat(sentMailBody("60231234@mju.ac.kr", 1L)).contains(LINK_PREFIX);
    }

    @Test
    void verify_쓸수있는링크_통과() {
        // given
        Account account = TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false);
        given(passwordResetTokenRepository.findByTokenHash(AuthTokenService.hash(RAW_TOKEN)))
                .willReturn(Optional.of(tokenIssuedAt(account, NOW.minusMinutes(29))));

        // when & then
        passwordResetService.verify(new PasswordResetVerifyRequestDto(RAW_TOKEN));
    }

    @Test
    void verify_만료된링크_410() {
        // given
        Account account = TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false);
        given(passwordResetTokenRepository.findByTokenHash(AuthTokenService.hash(RAW_TOKEN)))
                .willReturn(Optional.of(tokenIssuedAt(account, NOW.minusMinutes(30))));

        // when & then
        assertLinkInvalid(() -> passwordResetService.verify(new PasswordResetVerifyRequestDto(RAW_TOKEN)));
    }

    @Test
    void verify_없는토큰_410() {
        // given
        given(passwordResetTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

        // when & then
        assertLinkInvalid(() -> passwordResetService.verify(new PasswordResetVerifyRequestDto("unknown")));
    }

    @Test
    void confirm_정상_비밀번호바꾸고변경필요해제후모든기기로그아웃() {
        // given
        Account account = TestAccounts.withId(1L, AccountRole.STUDENT, "old-hash", true);
        PasswordResetToken token = tokenIssuedAt(account, NOW.minusMinutes(5));
        String tokenHash = AuthTokenService.hash(RAW_TOKEN);
        given(passwordResetTokenRepository.findAccountIdByTokenHash(tokenHash)).willReturn(Optional.of(1L));
        given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(account));
        given(passwordResetTokenRepository.findByTokenHashForUpdate(tokenHash)).willReturn(Optional.of(token));
        given(passwordEncoder.encode("newPass!2026")).willReturn("new-hash");

        // when
        passwordResetService.confirm(new PasswordResetConfirmRequestDto(RAW_TOKEN, "newPass!2026"));

        // then
        assertThat(account.getPasswordHash()).isEqualTo("new-hash");
        assertThat(account.isMustChangePassword()).isFalse();
        assertThat(account.getTokenVersion()).isEqualTo(1);
        assertThat(token.getUsedAt()).isEqualTo(NOW);
        verify(authTokenService).revokeAll(1L, NOW);
        verify(passwordResetTokenRepository).invalidateAllByAccountId(1L, NOW);
    }

    @Test
    void confirm_이미사용된링크_410이고비밀번호그대로() {
        // given
        Account account = TestAccounts.withId(1L, AccountRole.STUDENT, "old-hash", false);
        PasswordResetToken token = tokenIssuedAt(account, NOW.minusMinutes(5));
        token.markUsed(NOW.minusMinutes(1));
        String tokenHash = AuthTokenService.hash(RAW_TOKEN);
        given(passwordResetTokenRepository.findAccountIdByTokenHash(tokenHash)).willReturn(Optional.of(1L));
        given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(account));
        given(passwordResetTokenRepository.findByTokenHashForUpdate(tokenHash)).willReturn(Optional.of(token));

        // when & then
        assertLinkInvalid(() ->
                passwordResetService.confirm(new PasswordResetConfirmRequestDto(RAW_TOKEN, "newPass!2026")));
        assertThat(account.getPasswordHash()).isEqualTo("old-hash");
        verify(authTokenService, never()).revokeAll(anyLong(), any());
    }

    @Test
    void confirm_링크받은뒤사용중지된계정_410() {
        // given
        Account account = TestAccounts.withId(1L, AccountRole.STUDENT, "old-hash", false);
        PasswordResetToken token = tokenIssuedAt(account, NOW.minusMinutes(5));
        account.deactivate(NOW.minusMinutes(1));
        String tokenHash = AuthTokenService.hash(RAW_TOKEN);
        given(passwordResetTokenRepository.findAccountIdByTokenHash(tokenHash)).willReturn(Optional.of(1L));
        given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(account));
        given(passwordResetTokenRepository.findByTokenHashForUpdate(tokenHash)).willReturn(Optional.of(token));

        // when & then
        assertLinkInvalid(() ->
                passwordResetService.confirm(new PasswordResetConfirmRequestDto(RAW_TOKEN, "newPass!2026")));
        assertThat(token.getUsedAt()).isNull();
    }

    @Test
    void confirm_없는토큰_410() {
        // given
        given(passwordResetTokenRepository.findAccountIdByTokenHash(anyString())).willReturn(Optional.empty());

        // when & then
        assertLinkInvalid(() ->
                passwordResetService.confirm(new PasswordResetConfirmRequestDto("unknown", "newPass!2026")));
        verify(accountRepository, never()).findByIdForUpdate(anyLong());
    }
}
