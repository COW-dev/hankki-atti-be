package com.hankkiatti.domain.application.entity;

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
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자가 봉사시간을 고친 이력 (Notion 기능명세서 "봉사시간 수정"). "수정됨 · 1시간 → 1.5시간 · 관리자 · 일시"로 보여 준다.
 * 사유는 개인 사정이 들어갈 수 있어 로그에 남기지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "volunteer_hour_changes",
        indexes = @Index(name = "idx_volunteer_hour_changes_application_id", columnList = "application_id"))
public class VolunteerHourChange extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal beforeHours;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal afterHours;

    @Column(nullable = false, length = 200)
    private String reason;

    public VolunteerHourChange(Application application, Admin admin, BigDecimal beforeHours, BigDecimal afterHours,
                               String reason) {
        this.application = application;
        this.admin = admin;
        this.beforeHours = beforeHours;
        this.afterHours = afterHours;
        this.reason = reason;
    }
}
