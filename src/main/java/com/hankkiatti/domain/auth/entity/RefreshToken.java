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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * refresh 토큰. 원문은 저장하지 않고 SHA-256 해시만 보관한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_tokens_account", columnList = "account_id"))
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 10)
    private TokenAudience audience;

    // 발급 당시 계정의 tokenVersion. 비밀번호 변경·비활성화로 값이 바뀌면 이 토큰은 더 못 쓴다
    @Column(nullable = false)
    private int tokenVersion;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    public RefreshToken(Account account, String tokenHash, TokenAudience audience, int tokenVersion,
                        LocalDateTime expiresAt) {
        this.account = account;
        this.tokenHash = tokenHash;
        this.audience = audience;
        this.tokenVersion = tokenVersion;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public void revoke(LocalDateTime now) {
        if (!isRevoked()) {
            this.revokedAt = now;
        }
    }
}
