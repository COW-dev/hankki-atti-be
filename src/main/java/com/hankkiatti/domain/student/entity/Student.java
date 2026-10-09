package com.hankkiatti.domain.student.entity;

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

// 장애 유형·특이사항·연락처는 민감정보라 toString을 만들지 않는다
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "students")
public class Student extends BaseTimeEntity {

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

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 50)
    private String kakaoId;

    @Column(nullable = false, length = 100)
    private String schoolEmail;

    @Column(nullable = false, length = 30)
    private DisabilityType disabilityType;

    @Column(length = 1000)
    private String specialNote;

    @Column(nullable = false, length = 20)
    private CredentialMailStatus credentialMailStatus;

    private LocalDateTime credentialMailSentAt;

    public Student(Account account, String name, String studentNo, String phone, String kakaoId,
                   String schoolEmail, DisabilityType disabilityType, String specialNote) {
        this.account = account;
        this.name = name;
        this.studentNo = studentNo;
        this.phone = phone;
        this.kakaoId = kakaoId;
        this.schoolEmail = schoolEmail;
        this.disabilityType = disabilityType;
        this.specialNote = specialNote;
        this.credentialMailStatus = CredentialMailStatus.PENDING;
    }

    public void updateInfo(String name, String phone, String kakaoId, String schoolEmail,
                           DisabilityType disabilityType, String specialNote) {
        this.name = name;
        this.phone = phone;
        this.kakaoId = kakaoId;
        this.schoolEmail = schoolEmail;
        this.disabilityType = disabilityType;
        this.specialNote = specialNote;
    }

    public void markCredentialMailPending() {
        this.credentialMailStatus = CredentialMailStatus.PENDING;
        this.credentialMailSentAt = null;
    }

    public void markCredentialMailSent(LocalDateTime now) {
        this.credentialMailStatus = CredentialMailStatus.SENT;
        this.credentialMailSentAt = now;
    }

    public void markCredentialMailFailed() {
        this.credentialMailStatus = CredentialMailStatus.FAILED;
    }
}
