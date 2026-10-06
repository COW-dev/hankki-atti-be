package com.hankkiatti.domain.auth.service;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.auth.dto.request.LoginRequestDto;
import com.hankkiatti.domain.auth.dto.request.PasswordChangeRequestDto;
import com.hankkiatti.domain.auth.dto.response.AdminLoginResponseDto;
import com.hankkiatti.domain.auth.dto.response.LoginResponseDto;
import com.hankkiatti.domain.auth.dto.response.TokenResponseDto;
import com.hankkiatti.domain.auth.entity.TokenAudience;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final Clock clock;

    private volatile String dummyPasswordHash;

    @Transactional
    public AuthResult<LoginResponseDto> loginUser(LoginRequestDto request) {
        Account account = authenticate(request, TokenAudience.USER);
        IssuedTokens tokens = authTokenService.issue(account, TokenAudience.USER, now());

        LoginResponseDto body = new LoginResponseDto(
                tokens.accessToken(), tokens.expiresIn(), account.getRole(), account.isMustChangePassword());
        return new AuthResult<>(body, tokens.refreshToken());
    }

    @Transactional
    public AuthResult<AdminLoginResponseDto> loginAdmin(LoginRequestDto request) {
        Account account = authenticate(request, TokenAudience.ADMIN);
        Admin admin = adminRepository.findById(account.getId())
                .orElseThrow(() -> new AuthException(AuthErrorType.LOGIN_FAILED,
                        "관리자 프로필 없음, accountId=" + account.getId()));
        IssuedTokens tokens = authTokenService.issue(account, TokenAudience.ADMIN, now());

        AdminLoginResponseDto body = new AdminLoginResponseDto(
                tokens.accessToken(), tokens.expiresIn(), admin.getName(), admin.getGrade());
        return new AuthResult<>(body, tokens.refreshToken());
    }

    // 회전 실패 시 계정 토큰 일괄 폐기를 커밋해야 하므로 여기서 트랜잭션을 열지 않고 AuthTokenService에 맡긴다
    public AuthResult<TokenResponseDto> refresh(String refreshToken, TokenAudience audience) {
        IssuedTokens tokens = authTokenService.rotate(refreshToken, audience, now());
        return new AuthResult<>(new TokenResponseDto(tokens.accessToken(), tokens.expiresIn()), tokens.refreshToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        authTokenService.revoke(refreshToken, now());
    }

    /**
     * 비밀번호를 바꾸고 다른 기기의 로그인을 모두 끊는다. 지금 기기에는 새 토큰을 준다.
     */
    @Transactional
    public AuthResult<TokenResponseDto> changePassword(Long accountId, PasswordChangeRequestDto request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AuthException(AuthErrorType.UNAUTHENTICATED, "accountId=" + accountId));

        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new AuthException(AuthErrorType.CURRENT_PASSWORD_MISMATCH);
        }
        // 첫 로그인이면 현재 비밀번호가 센터가 발급한 초기 비밀번호다 → 같은 값으로 바꿀 수 없다
        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new AuthException(AuthErrorType.SAME_AS_CURRENT_PASSWORD);
        }

        LocalDateTime now = now();
        account.changePassword(passwordEncoder.encode(request.newPassword()));
        authTokenService.revokeAll(accountId, now);
        IssuedTokens tokens = authTokenService.issue(account, TokenAudience.USER, now);
        return new AuthResult<>(new TokenResponseDto(tokens.accessToken(), tokens.expiresIn()), tokens.refreshToken());
    }

    /**
     * 아이디·비밀번호가 틀리면 같은 응답을 준다. 비활성 계정 안내는 비밀번호가 맞을 때만 준다 — 계정 존재 여부가 드러나지 않게.
     */
    private Account authenticate(LoginRequestDto request, TokenAudience audience) {
        Account account = accountRepository.findByLoginId(Account.normalizeLoginId(request.loginId()))
                .filter(found -> audience.allows(found.getRole()))
                .orElse(null);

        if (account == null) {
            // 없는 아이디도 bcrypt 비교를 한 번 해서 응답 시간으로 계정 존재 여부를 알 수 없게 한다
            passwordEncoder.matches(request.password(), dummyPasswordHash());
            throw new AuthException(AuthErrorType.LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new AuthException(AuthErrorType.LOGIN_FAILED, "accountId=" + account.getId());
        }
        if (!account.isActive()) {
            throw new AuthException(AuthErrorType.ACCOUNT_DEACTIVATED, "accountId=" + account.getId());
        }

        account.recordLogin(now());
        return account;
    }

    private String dummyPasswordHash() {
        if (dummyPasswordHash == null) {
            dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
        }
        return dummyPasswordHash;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
