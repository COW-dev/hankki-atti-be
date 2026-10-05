package com.hankkiatti.domain.account.entity;

import com.hankkiatti.domain.account.exception.AccountErrorType;
import com.hankkiatti.domain.account.exception.AccountException;
import com.hankkiatti.domain.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "accounts")
public class Account extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String loginId;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private AccountRole role;

    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(nullable = false)
    private boolean mustChangePassword;

    @Column(nullable = false)
    private boolean accessibilityMode;

    // 값이 바뀌면 이전에 발급된 토큰이 모두 무효가 된다
    @Column(nullable = false)
    private int tokenVersion;

    private LocalDateTime lastLoginAt;

    private LocalDateTime deactivatedAt;

    public Account(String loginId, String passwordHash, AccountRole role,
                   boolean mustChangePassword, boolean accessibilityMode) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = AccountStatus.ACTIVE;
        this.mustChangePassword = mustChangePassword;
        this.accessibilityMode = accessibilityMode;
        this.tokenVersion = 0;
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.mustChangePassword = false;
        this.tokenVersion++;
    }

    public void issueTemporaryPassword(String newHash) {
        this.passwordHash = newHash;
        this.mustChangePassword = true;
        this.tokenVersion++;
    }

    public void changeAccessibilityMode(boolean on) {
        this.accessibilityMode = on;
    }

    public void recordLogin(LocalDateTime now) {
        this.lastLoginAt = now;
    }

    public void deactivate(LocalDateTime now) {
        if (!isActive()) {
            throw new AccountException(AccountErrorType.ALREADY_INACTIVE, "accountId=" + id);
        }
        this.status = AccountStatus.INACTIVE;
        this.deactivatedAt = now;
        this.tokenVersion++;
    }

    public void activate() {
        if (isActive()) {
            throw new AccountException(AccountErrorType.ALREADY_ACTIVE, "accountId=" + id);
        }
        this.status = AccountStatus.ACTIVE;
        this.deactivatedAt = null;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }
}
