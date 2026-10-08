package com.hankkiatti.domain.auth.entity;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 비밀번호 재설정 링크의 토큰. 원문은 메일에만 담고 DB에는 SHA-256 해시만 보관한다.
 * 30분 동안 한 번만 쓸 수 있고, 새로 발급하면 이전 토큰은 무효가 된다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "password_reset_tokens",
        indexes = @Index(name = "idx_password_reset_tokens_account", columnList = "account_id"))
public class PasswordResetToken extends BaseTimeEntity {

    public static final Duration VALIDITY = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // 비밀번호를 바꿨거나, 새 링크를 발급해 무효가 된 시각
    private LocalDateTime usedAt;

    public PasswordResetToken(Account account, String tokenHash, LocalDateTime issuedAt) {
        this.account = account;
        this.tokenHash = tokenHash;
        this.expiresAt = issuedAt.plus(VALIDITY);
    }

    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(LocalDateTime now) {
        if (usedAt == null) {
            this.usedAt = now;
        }
    }

    /**
     * 지금부터 거슬러 period 안에 발급됐는지. 발급 시각은 만료 시각에서 유효 시간을 빼서 구한다 —
     * createdAt은 JPA Auditing이 시스템 시계로 채워 주입된 Clock과 어긋날 수 있어서다.
     */
    public boolean issuedWithin(Duration period, LocalDateTime now) {
        LocalDateTime issuedAt = expiresAt.minus(VALIDITY);
        return issuedAt.isAfter(now.minus(period));
    }
}
