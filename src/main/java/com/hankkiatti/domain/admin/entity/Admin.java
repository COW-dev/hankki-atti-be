package com.hankkiatti.domain.admin.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "admins")
public class Admin extends BaseTimeEntity {

    @Id
    private Long accountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private AdminGrade grade;

    public Admin(Account account, String name, AdminGrade grade) {
        this.account = account;
        this.name = name;
        this.grade = grade;
    }

    public boolean isFull() {
        return grade == AdminGrade.FULL;
    }
}
