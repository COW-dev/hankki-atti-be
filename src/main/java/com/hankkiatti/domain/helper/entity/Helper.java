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
}
