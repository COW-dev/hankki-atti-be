package com.hankkiatti.domain.auth.service;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.auth.dto.request.PasswordResetConfirmRequestDto;
import com.hankkiatti.domain.auth.dto.request.PasswordResetRequestDto;
import com.hankkiatti.domain.auth.dto.request.PasswordResetVerifyRequestDto;
import com.hankkiatti.domain.auth.entity.PasswordResetToken;
import com.hankkiatti.domain.auth.entity.TokenAudience;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.auth.repository.PasswordResetTokenRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.service.MailOutboxService;
import com.hankkiatti.domain.mail.service.MailProperties;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 비밀번호 재설정 (기능명세서 "비밀번호 재설정", L-03). 장애학생·도우미만 쓴다 — 관리자는 비밀번호 찾기가 없다.
 * 요청 결과는 계정이 있든 없든 같다. 계정 존재 여부가 드러나지 않게 하려는 것이다.
 *
 * <p>요청·완료 모두 계정 행을 먼저 잠근다. 같은 아이디의 동시 요청에서 메일이 한 번만 나가고,
 * 같은 링크로 동시에 완료해도 한 번만 바뀐다. 잠그는 순서(계정 → 토큰)를 맞춰 서로 기다리며 멈추는 일도 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    // 같은 아이디는 10분에 1번만 실제로 보낸다
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(10);
    static final String MAIL_SUBJECT = "[한끼아띠] 비밀번호 재설정 안내";

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    // 토큰을 # 뒤에 둔다. 브라우저가 서버로 보내지 않아 접근 로그·Referer에 남지 않는다
    private static final String LINK_PATH = "/password-reset#token=";

    private final AccountRepository accountRepository;
    private final StudentRepository studentRepository;
    private final HelperRepository helperRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuthTokenService authTokenService;
    private final MailOutboxService mailOutboxService;
    private final MailProperties mailProperties;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * 등록된 이메일로 30분·1회용 재설정 링크를 보낸다. 보낼 대상이 아니거나 10분 안에 보낸 적이 있으면 조용히 끝낸다.
     */
    @Transactional
    public void request(PasswordResetRequestDto request) {
        // 잠금 조회를 트랜잭션의 첫 쿼리로 둔다. MySQL(REPEATABLE READ)은 첫 일반 조회 시점의 스냅숏을 계속 보므로,
        // 잠금 전에 다른 조회를 하면 먼저 커밋된 동시 요청의 토큰을 못 보고 메일이 두 번 나갈 수 있다
        Account account = accountRepository.findByLoginIdForUpdate(Account.normalizeLoginId(request.loginId()))
                .filter(this::canReset)
                .orElse(null);
        if (account == null) {
            return;
        }

        LocalDateTime now = now();
        boolean sentRecently = passwordResetTokenRepository.findTopByAccountIdOrderByIdDesc(account.getId())
                .filter(token -> token.issuedWithin(RESEND_INTERVAL, now))
                .isPresent();
        if (sentRecently) {
            log.info("비밀번호 재설정 메일 생략 (10분 이내 재요청): accountId={}", account.getId());
            return;
        }

        Optional<String> recipient = recipientOf(account);
        if (recipient.isEmpty()) {
            log.warn("비밀번호 재설정 메일 생략 (프로필 없음): accountId={}", account.getId());
            return;
        }

        // 새 링크를 보내면 이전 링크는 못 쓴다
        passwordResetTokenRepository.invalidateAllByAccountId(account.getId(), now);
        String rawToken = newToken();
        passwordResetTokenRepository.save(new PasswordResetToken(account, AuthTokenService.hash(rawToken), now));
        mailOutboxService.enqueue(MailType.PASSWORD_RESET, recipient.get(), MAIL_SUBJECT, mailBody(rawToken),
                account.getId());
        // 아이디·이메일은 로그에 남기지 않는다
        log.info("비밀번호 재설정 메일 요청: accountId={}", account.getId());
    }

    /**
     * 메일 링크를 열었을 때 아직 쓸 수 있는 링크인지 확인한다. 토큰은 쓰지 않는다 — 메일 보안 검사가 링크를 미리 열어도 소모되지 않게.
     */
    @Transactional(readOnly = true)
    public void verify(PasswordResetVerifyRequestDto request) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(AuthTokenService.hash(request.token()))
                .orElseThrow(() -> new AuthException(AuthErrorType.PASSWORD_RESET_LINK_INVALID));
        if (!token.isUsable(now()) || !canReset(token.getAccount())) {
            throw new AuthException(AuthErrorType.PASSWORD_RESET_LINK_INVALID, "tokenId=" + token.getId());
        }
    }

    /**
     * 새 비밀번호로 바꾸고 모든 기기에서 로그아웃한다. 변경 필요 상태(첫 로그인 전)도 풀린다.
     */
    @Transactional
    public void confirm(PasswordResetConfirmRequestDto request) {
        String tokenHash = AuthTokenService.hash(request.token());
        Long accountId = passwordResetTokenRepository.findAccountIdByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthException(AuthErrorType.PASSWORD_RESET_LINK_INVALID));

        // 요청과 같은 순서(계정 → 토큰)로 잠근다
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AuthException(AuthErrorType.PASSWORD_RESET_LINK_INVALID, "accountId=" + accountId));
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new AuthException(AuthErrorType.PASSWORD_RESET_LINK_INVALID, "accountId=" + accountId));

        LocalDateTime now = now();
        if (!token.isUsable(now) || !canReset(account)) {
            throw new AuthException(AuthErrorType.PASSWORD_RESET_LINK_INVALID, "tokenId=" + token.getId());
        }

        token.markUsed(now);
        // tokenVersion이 올라 access 토큰이 바로 무효가 되고, refresh 토큰은 모두 폐기한다
        account.changePassword(passwordEncoder.encode(request.newPassword()));
        authTokenService.revokeAll(accountId, now);
        passwordResetTokenRepository.invalidateAllByAccountId(accountId, now);
        log.info("비밀번호 재설정 완료: accountId={}", accountId);
    }

    private boolean canReset(Account account) {
        return account.isActive() && TokenAudience.USER.allows(account.getRole());
    }

    // 학생은 학교 이메일, 도우미는 가입 이메일
    private Optional<String> recipientOf(Account account) {
        return switch (account.getRole()) {
            case STUDENT -> studentRepository.findById(account.getId()).map(Student::getSchoolEmail);
            case HELPER -> helperRepository.findById(account.getId()).map(Helper::getEmail);
            case ADMIN -> Optional.empty();
        };
    }

    private String mailBody(String rawToken) {
        String link = StringUtils.trimTrailingCharacter(mailProperties.linkBaseUrl(), '/') + LINK_PATH + rawToken;
        return """
                한끼아띠 비밀번호 재설정을 요청하셨어요.
                아래 링크에서 새 비밀번호를 정해 주세요. 링크는 30분 동안 한 번만 쓸 수 있어요.

                %s

                요청하지 않으셨다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않아요.

                명지대학교 장애학생지원센터 02-300-1529
                """.formatted(link);
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
