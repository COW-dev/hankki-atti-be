package com.hankkiatti.domain.helper.service;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.auth.repository.PasswordResetTokenRepository;
import com.hankkiatti.domain.helper.exception.HelperErrorType;
import com.hankkiatti.domain.helper.exception.HelperException;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 도우미 계정 상태만 바꾸는 트랜잭션 (관리자 비활성화·재활성화). 비활성화의 매칭·예비 해제는 커밋 뒤 지원마다 따로 한다 —
 * {@link AdminHelperDeactivationService}가 조율하고, 트랜잭션을 나누려고 별도 빈으로 둔다.
 */
@Service
@RequiredArgsConstructor
public class HelperAccountService {

    private final AccountRepository accountRepository;
    private final HelperRepository helperRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * 계정 → 도우미 행 순으로 잠그고 비활성화한다. tokenVersion이 올라 로그인·기존 토큰이 바로 막힌다.
     * 도우미 행까지 잠그는 이유: 지원도 도우미 행을 잠그므로, 진행 중이던 지원은 이 커밋 전에 끝나 해제 대상에 잡히고
     * 이 뒤에 잠근 지원은 비활성 계정을 보고 막힌다 (ApplicationService.apply).
     *
     * @return 지금 비활성화했으면 true, 이미 비활성이었으면 false (남은 매칭·예비 정리만 다시 한다)
     */
    @Transactional
    public boolean deactivate(Long helperId, LocalDateTime now) {
        Account account = lockHelperAccount(helperId);
        helperRepository.findByIdForUpdate(helperId)
                .orElseThrow(() -> notFound(helperId));
        if (!account.isActive()) {
            return false;
        }
        account.deactivate(now);
        passwordResetTokenRepository.invalidateAllByAccountId(helperId, now);
        return true;
    }

    /**
     * 다시 로그인할 수 있게 한다. 비활성화 때 해제된 매칭·예비는 되돌리지 않는다 (이미 승격·모집 재개됐다).
     */
    @Transactional
    public Account activate(Long helperId) {
        Account account = lockHelperAccount(helperId);
        if (account.isActive()) {
            throw new HelperException(HelperErrorType.ALREADY_ACTIVE, "helperId=" + helperId);
        }
        account.activate();
        return account;
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long helperId) {
        return accountRepository.findById(helperId)
                .filter(found -> found.getRole() == AccountRole.HELPER)
                .orElseThrow(() -> notFound(helperId));
    }

    private Account lockHelperAccount(Long helperId) {
        return accountRepository.findByIdForUpdate(helperId)
                .filter(found -> found.getRole() == AccountRole.HELPER)
                .orElseThrow(() -> notFound(helperId));
    }

    private static HelperException notFound(Long helperId) {
        return new HelperException(HelperErrorType.NOT_FOUND, "helperId=" + helperId);
    }
}
