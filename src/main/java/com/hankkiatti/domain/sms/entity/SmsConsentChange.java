package com.hankkiatti.domain.sms.entity;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.admin.entity.Admin;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문자 수신 동의 이력. 가입·등록 때 첫 상태를, 그 뒤에는 동의·철회가 실제로 바뀔 때마다 한 줄 남긴다 (2026-10-11 결정).
 * 지금 상태는 장애학생·도우미 프로필의 sms_consented_at에 있다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "sms_consent_changes",
        indexes = @Index(name = "idx_sms_consent_changes_account_id", columnList = "account_id"))
public class SmsConsentChange extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 동의한 사람 (장애학생·도우미 계정)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    private boolean consented;

    @Column(nullable = false, length = 20)
    private SmsConsentSource source;

    // 관리자가 바꿨을 때만
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Admin admin;

    public SmsConsentChange(Account account, boolean consented, SmsConsentSource source, Admin admin) {
        this.account = account;
        this.consented = consented;
        this.source = source;
        this.admin = admin;
    }
}
