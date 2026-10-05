package com.hankkiatti.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.entity.AdminGrade;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.auth.dto.request.LoginRequestDto;
import com.hankkiatti.domain.auth.dto.request.PasswordChangeRequestDto;
import com.hankkiatti.domain.auth.dto.response.AdminLoginResponseDto;
import com.hankkiatti.domain.auth.dto.response.LoginResponseDto;
import com.hankkiatti.domain.auth.dto.response.TokenResponseDto;
import com.hankkiatti.domain.auth.entity.TokenAudience;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.support.TestAccounts;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 12, 0);
    private static final IssuedTokens TOKENS = new IssuedTokens("access", 1800, "refresh");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthTokenService authTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        authService = new AuthService(accountRepository, adminRepository, passwordEncoder, authTokenService, clock);
    }

    @Test
    void loginUser_올바른아이디비밀번호_토큰과변경필요여부반환() {
        // given
        Account student = TestAccounts.withId(1L, AccountRole.STUDENT, "hash", true);
        given(accountRepository.findByLoginId("60231234")).willReturn(Optional.of(student));
        given(passwordEncoder.matches("pw", "hash")).willReturn(true);
        given(authTokenService.issue(student, TokenAudience.USER, NOW)).willReturn(TOKENS);

        // when
        AuthResult<LoginResponseDto> result = authService.loginUser(new LoginRequestDto(" 60231234 ", "pw"));

        // then
        assertThat(result.body().accessToken()).isEqualTo("access");
        assertThat(result.body().role()).isEqualTo(AccountRole.STUDENT);
        assertThat(result.body().mustChangePassword()).isTrue();
        assertThat(result.refreshToken()).isEqualTo("refresh");
        assertThat(student.getLastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void loginUser_없는아이디_로그인실패하고비교는한번수행() {
        // given
        given(accountRepository.findByLoginId("nobody")).willReturn(Optional.empty());
        given(passwordEncoder.encode(anyString())).willReturn("dummy-hash");

        // when & then
        assertThatThrownBy(() -> authService.loginUser(new LoginRequestDto("nobody", "pw")))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.LOGIN_FAILED);
        verify(passwordEncoder).matches("pw", "dummy-hash");
    }

    @Test
    void loginUser_비밀번호틀림_로그인실패() {
        // given
        Account helper = TestAccounts.withId(2L, AccountRole.HELPER, "hash", false);
        given(accountRepository.findByLoginId("helper@mju.ac.kr")).willReturn(Optional.of(helper));
        given(passwordEncoder.matches("wrong", "hash")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.loginUser(new LoginRequestDto("helper@mju.ac.kr", "wrong")))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.LOGIN_FAILED);
    }

    @Test
    void loginUser_비활성계정에맞는비밀번호_사용중지안내() {
        // given
        Account student = TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false);
        student.deactivate(NOW.minusDays(1));
        given(accountRepository.findByLoginId("60231234")).willReturn(Optional.of(student));
        given(passwordEncoder.matches("pw", "hash")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.loginUser(new LoginRequestDto("60231234", "pw")))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCOUNT_DEACTIVATED);
    }

    @Test
    void loginUser_비활성계정에틀린비밀번호_일반실패로응답() {
        // given
        Account student = TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false);
        student.deactivate(NOW.minusDays(1));
        given(accountRepository.findByLoginId("60231234")).willReturn(Optional.of(student));
        given(passwordEncoder.matches("wrong", "hash")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.loginUser(new LoginRequestDto("60231234", "wrong")))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.LOGIN_FAILED);
    }

    @Test
    void loginUser_관리자계정으로사용자로그인_로그인실패() {
        // given
        Account admin = TestAccounts.withId(3L, AccountRole.ADMIN, "hash", false);
        given(accountRepository.findByLoginId("center01")).willReturn(Optional.of(admin));
        given(passwordEncoder.encode(anyString())).willReturn("dummy-hash");

        // when & then
        assertThatThrownBy(() -> authService.loginUser(new LoginRequestDto("center01", "pw")))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.LOGIN_FAILED);
        verify(authTokenService, never()).issue(any(), any(), any());
    }

    @Test
    void loginAdmin_관리자계정_이름과권한등급반환() {
        // given
        Account account = TestAccounts.withId(3L, AccountRole.ADMIN, "hash", false);
        given(accountRepository.findByLoginId("center01")).willReturn(Optional.of(account));
        given(passwordEncoder.matches("pw", "hash")).willReturn(true);
        given(adminRepository.findById(3L)).willReturn(Optional.of(new Admin(account, "김센터", AdminGrade.LIMITED)));
        given(authTokenService.issue(account, TokenAudience.ADMIN, NOW)).willReturn(TOKENS);

        // when
        AuthResult<AdminLoginResponseDto> result = authService.loginAdmin(new LoginRequestDto("center01", "pw"));

        // then
        assertThat(result.body().name()).isEqualTo("김센터");
        assertThat(result.body().grade()).isEqualTo(AdminGrade.LIMITED);
    }

    @Test
    void loginAdmin_관리자프로필없음_로그인실패() {
        // given
        Account account = TestAccounts.withId(3L, AccountRole.ADMIN, "hash", false);
        given(accountRepository.findByLoginId("center01")).willReturn(Optional.of(account));
        given(passwordEncoder.matches("pw", "hash")).willReturn(true);
        given(adminRepository.findById(3L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.loginAdmin(new LoginRequestDto("center01", "pw")))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.LOGIN_FAILED);
    }

    @Test
    void refresh_회전위임_새토큰반환() {
        // given
        given(authTokenService.rotate("refresh", TokenAudience.USER, NOW)).willReturn(TOKENS);

        // when
        AuthResult<TokenResponseDto> result = authService.refresh("refresh", TokenAudience.USER);

        // then
        assertThat(result.body().accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
    }

    @Test
    void logout_refresh토큰폐기위임() {
        // when
        authService.logout("refresh");

        // then
        verify(authTokenService).revoke("refresh", NOW);
    }

    @Test
    void changePassword_첫로그인변경_변경필요해제하고다른기기로그아웃() {
        // given
        Account student = TestAccounts.withId(1L, AccountRole.STUDENT, "initial-hash", true);
        given(accountRepository.findById(1L)).willReturn(Optional.of(student));
        given(passwordEncoder.matches("initial!1", "initial-hash")).willReturn(true);
        given(passwordEncoder.matches("newPass!2", "initial-hash")).willReturn(false);
        given(passwordEncoder.encode("newPass!2")).willReturn("new-hash");
        given(authTokenService.issue(eq(student), eq(TokenAudience.USER), eq(NOW))).willReturn(TOKENS);

        // when
        AuthResult<TokenResponseDto> result = authService.changePassword(1L,
                new PasswordChangeRequestDto("initial!1", "newPass!2"));

        // then
        assertThat(student.getPasswordHash()).isEqualTo("new-hash");
        assertThat(student.isMustChangePassword()).isFalse();
        assertThat(student.getTokenVersion()).isEqualTo(1);
        verify(authTokenService).revokeAll(1L, NOW);
        assertThat(result.body().accessToken()).isEqualTo("access");
    }

    @Test
    void changePassword_현재비밀번호틀림_예외() {
        // given
        Account student = TestAccounts.withId(1L, AccountRole.STUDENT, "hash", true);
        given(accountRepository.findById(1L)).willReturn(Optional.of(student));
        given(passwordEncoder.matches("wrong", "hash")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.changePassword(1L, new PasswordChangeRequestDto("wrong", "newPass!2")))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.CURRENT_PASSWORD_MISMATCH);
    }

    @Test
    void changePassword_현재와같은비밀번호_예외() {
        // given
        Account student = TestAccounts.withId(1L, AccountRole.STUDENT, "hash", true);
        given(accountRepository.findById(1L)).willReturn(Optional.of(student));
        given(passwordEncoder.matches("same!Pw1", "hash")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.changePassword(1L, new PasswordChangeRequestDto("same!Pw1", "same!Pw1")))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.SAME_AS_CURRENT_PASSWORD);
        assertThat(student.isMustChangePassword()).isTrue();
    }

    @Test
    void changePassword_계정없음_인증필요() {
        // given
        given(accountRepository.findById(9L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.changePassword(9L, new PasswordChangeRequestDto("a", "b")))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.UNAUTHENTICATED);
    }
}
