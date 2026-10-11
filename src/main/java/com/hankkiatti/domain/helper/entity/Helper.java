package com.hankkiatti.domain.helper.entity;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "helpers")
public class Helper extends BaseTimeEntity {

    @Id
    private Long accountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 8)
    private String studentNo;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 50)
    private String kakaoId;

    @Column(nullable = false)
    private boolean attiMember;

    @Column(nullable = false)
    private LocalDateTime guideConfirmedAt;

    // 문자 수신 동의 시각. NULL이면 미동의 — 문자 알림을 보내지 않는다 (메일·인앱은 그대로)
    private LocalDateTime smsConsentedAt;

    public Helper(Account account, String name, String studentNo, String email, String phone,
                  String kakaoId, boolean attiMember, LocalDateTime guideConfirmedAt) {
        this.account = account;
        this.name = name;
        this.studentNo = studentNo;
        this.email = email;
        this.phone = phone;
        this.kakaoId = kakaoId;
        this.attiMember = attiMember;
        this.guideConfirmedAt = guideConfirmedAt;
    }

    public void updateContact(String phone, String kakaoId) {
        this.phone = phone;
        this.kakaoId = kakaoId;
    }

    /**
     * 문자 수신 동의를 바꾼다. 동의하면 그 시각, 철회하면 비운다.
     *
     * @return 실제로 바뀌었으면 true (이미 같은 상태면 false — 이력을 남기지 않는다)
     */
    public boolean changeSmsConsent(boolean consent, LocalDateTime now) {
        if (consent == (smsConsentedAt != null)) {
            return false;
        }
        this.smsConsentedAt = consent ? now : null;
        return true;
    }

    public boolean isSmsConsented() {
        return smsConsentedAt != null;
    }
}
