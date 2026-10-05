package com.hankkiatti.domain.application.entity;

import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.common.BaseTimeEntity;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 같은 신청에 같은 도우미가 다시 지원할 수 있어 (help_request_id, helper_id) 유니크 제약을 두지 않는다
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "applications",
        indexes = {
                @Index(name = "idx_applications_help_request_id_status_applied_at",
                        columnList = "help_request_id, status, applied_at"),
                @Index(name = "idx_applications_helper_id_status", columnList = "helper_id, status"),
                @Index(name = "idx_applications_help_request_id_helper_id", columnList = "help_request_id, helper_id")
        }
)
public class Application extends BaseTimeEntity {

    private static final BigDecimal COMPLETED_VOLUNTEER_HOURS = new BigDecimal("1.0");
    private static final BigDecimal NO_SHOW_VOLUNTEER_HOURS = new BigDecimal("0.0");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "help_request_id", nullable = false)
    private HelpRequest helpRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "helper_id", nullable = false)
    private Helper helper;

    @Column(nullable = false, length = 30)
    private ApplicationStatus status;

    // 선착순 순서 기준이라 마이크로초까지 저장한다
    @Column(nullable = false)
    private LocalDateTime appliedAt;

    private LocalDateTime matchedAt;

    private LocalDateTime promotedAt;

    private LocalDateTime canceledAt;

    private LocalDateTime feedbackPromptedAt;

    @Column(length = 30)
    private CancelReason cancelReason;

    @Column(length = 200)
    private String cancelReasonDetail;

    @Column(length = 20)
    private ApplicationAfterAction afterAction;

    @Column(precision = 3, scale = 1)
    private BigDecimal volunteerHours;

    public Application(HelpRequest helpRequest, Helper helper, LocalDateTime appliedAt) {
        this.helpRequest = helpRequest;
        this.helper = helper;
        this.appliedAt = appliedAt;
        this.status = ApplicationStatus.WAITING;
    }

    public void match(LocalDateTime now) {
        requireStatus(ApplicationStatus.WAITING);
        this.status = ApplicationStatus.MATCHED;
        this.matchedAt = now;
    }

    public void promote(LocalDateTime now, boolean responseRequired) {
        requireStatus(ApplicationStatus.WAITING);
        this.promotedAt = now;
        if (responseRequired) {
            this.status = ApplicationStatus.PROMOTION_PENDING;
            return;
        }
        this.status = ApplicationStatus.MATCHED;
        this.matchedAt = now;
    }

    public void acceptPromotion(LocalDateTime now) {
        requireStatus(ApplicationStatus.PROMOTION_PENDING);
        this.status = ApplicationStatus.MATCHED;
        this.matchedAt = now;
    }

    public void declinePromotion(LocalDateTime now) {
        requireStatus(ApplicationStatus.PROMOTION_PENDING);
        this.status = ApplicationStatus.PROMOTION_DECLINED;
        this.canceledAt = now;
    }

    public void withdraw(LocalDateTime now) {
        requireStatus(ApplicationStatus.WAITING);
        this.status = ApplicationStatus.WITHDRAWN;
        this.canceledAt = now;
    }

    public void exclude() {
        requireStatus(ApplicationStatus.WAITING);
        this.status = ApplicationStatus.EXCLUDED;
    }

    public void expire() {
        requireStatus(ApplicationStatus.WAITING);
        this.status = ApplicationStatus.EXPIRED;
    }

    public void cancelByHelper(CancelReason reason, String detail, LocalDateTime now) {
        requireStatus(ApplicationStatus.MATCHED);
        if (reason == CancelReason.ADMIN_DEACTIVATED) {
            throw new ApplicationException(ApplicationErrorType.INVALID_CANCEL_REASON, "applicationId=" + id);
        }
        if (reason == CancelReason.OTHER && (detail == null || detail.isBlank())) {
            throw new ApplicationException(ApplicationErrorType.CANCEL_REASON_DETAIL_REQUIRED);
        }
        this.status = ApplicationStatus.HELPER_CANCELED;
        this.cancelReason = reason;
        this.cancelReasonDetail = detail;
        this.canceledAt = now;
    }

    // 도우미 계정 비활성화: 예비 자리는 빠짐으로, 매칭·승격 대기는 도우미 취소로 처리한다
    public void cancelByDeactivation(LocalDateTime now) {
        requireStatus(ApplicationStatus.MATCHED, ApplicationStatus.PROMOTION_PENDING, ApplicationStatus.WAITING);
        this.status = status == ApplicationStatus.WAITING
                ? ApplicationStatus.WITHDRAWN
                : ApplicationStatus.HELPER_CANCELED;
        this.cancelReason = CancelReason.ADMIN_DEACTIVATED;
        this.canceledAt = now;
    }

    public void cancelByStudent() {
        requireStatus(ApplicationStatus.MATCHED, ApplicationStatus.PROMOTION_PENDING, ApplicationStatus.WAITING);
        this.status = ApplicationStatus.STUDENT_CANCELED;
    }

    public void recordAfterAction(ApplicationAfterAction action) {
        requireStatus(ApplicationStatus.HELPER_CANCELED);
        this.afterAction = action;
    }

    public void complete() {
        requireStatus(ApplicationStatus.MATCHED);
        this.status = ApplicationStatus.COMPLETED;
        this.volunteerHours = COMPLETED_VOLUNTEER_HOURS;
    }

    public void markNoShow() {
        requireStatus(ApplicationStatus.COMPLETED);
        this.status = ApplicationStatus.NO_SHOW;
        this.volunteerHours = NO_SHOW_VOLUNTEER_HOURS;
    }

    public boolean isActive() {
        return status == ApplicationStatus.MATCHED
                || status == ApplicationStatus.PROMOTION_PENDING
                || status == ApplicationStatus.WAITING;
    }

    private void requireStatus(ApplicationStatus... allowed) {
        if (!Arrays.asList(allowed).contains(status)) {
            throw new ApplicationException(ApplicationErrorType.INVALID_STATUS,
                    "applicationId=" + id + ", status=" + status);
        }
    }
}
