package com.hankkiatti.domain.helprequest.entity;

import com.hankkiatti.domain.common.BaseTimeEntity;
import com.hankkiatti.domain.helprequest.exception.HelpRequestErrorType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestException;
import com.hankkiatti.domain.student.entity.Student;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "help_requests",
        indexes = {
                @Index(name = "idx_help_requests_status_start_at", columnList = "status, start_at"),
                @Index(name = "idx_help_requests_student_id_start_at", columnList = "student_id, start_at"),
                @Index(name = "idx_help_requests_start_at", columnList = "start_at")
        }
)
public class HelpRequest extends BaseTimeEntity {

    // 이용 시간은 식사 시작부터 1시간으로 고정이다
    public static final long USAGE_HOURS = 1;
    private static final long NO_SHOW_REPORT_HOURS = 24;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "help_request_help_types", joinColumns = @JoinColumn(name = "help_request_id"))
    @Column(name = "help_type", nullable = false, length = 20)
    private Set<HelpType> helpTypes = new HashSet<>();

    @Column(length = 100)
    private String otherHelpText;

    @Column(length = 200)
    private String memo;

    @Column(nullable = false, length = 20)
    private HelpRequestStatus status;

    @Column(nullable = false)
    private boolean helperChanged;

    private LocalDateTime firstMatchedAt;

    private LocalDateTime canceledAt;

    private LocalDateTime completedAt;

    private LocalDateTime noShowReportedAt;

    private LocalDateTime feedbackPromptedAt;

    @Column(length = 30)
    private RequestCancelType cancelType;

    public HelpRequest(Student student, LocalDateTime startAt, Set<HelpType> helpTypes,
                       String otherHelpText, String memo) {
        if (helpTypes == null || helpTypes.isEmpty()) {
            throw new HelpRequestException(HelpRequestErrorType.HELP_TYPE_REQUIRED);
        }
        if (helpTypes.contains(HelpType.OTHER) && (otherHelpText == null || otherHelpText.isBlank())) {
            throw new HelpRequestException(HelpRequestErrorType.OTHER_HELP_TEXT_REQUIRED);
        }
        this.student = student;
        this.startAt = startAt;
        this.endAt = startAt.plusHours(USAGE_HOURS);
        this.helpTypes = new HashSet<>(helpTypes);
        this.otherHelpText = otherHelpText;
        this.memo = memo;
        this.status = HelpRequestStatus.RECRUITING;
    }

    // 밖에서 컬렉션을 고쳐 생성자 검증(도움 유형 1개 이상)을 우회하지 못하게 읽기 전용으로 내준다
    public Set<HelpType> getHelpTypes() {
        return Collections.unmodifiableSet(helpTypes);
    }

    public void match(LocalDateTime now) {
        requireStatus(HelpRequestStatus.RECRUITING);
        this.status = HelpRequestStatus.MATCHED;
        // 재매칭돼도 첫 매칭 시각은 유지한다 (신청→매칭 소요 시간 지표)
        if (this.firstMatchedAt == null) {
            this.firstMatchedAt = now;
        }
    }

    public void changeHelper() {
        requireStatus(HelpRequestStatus.MATCHED);
        this.helperChanged = true;
    }

    public void reopen() {
        requireStatus(HelpRequestStatus.MATCHED);
        this.status = HelpRequestStatus.RECRUITING;
    }

    public void fail() {
        requireStatus(HelpRequestStatus.RECRUITING);
        this.status = HelpRequestStatus.FAILED;
    }

    // 승격된 도우미가 식사 시작까지 응답하지 않았다. 확정된 도우미가 없으니 매칭 실패로 끝낸다 (2026-10-10 결정)
    public void failUnanswered() {
        requireStatus(HelpRequestStatus.MATCHED);
        this.status = HelpRequestStatus.FAILED;
    }

    public void withdraw(LocalDateTime now) {
        requireStatus(HelpRequestStatus.RECRUITING);
        // 식사가 시작된 모집 중 신청은 스케줄러가 곧 매칭 실패로 바꾼다. 그 사이 철회되면 매칭 실패가 취소로 잡힌다
        if (!now.isBefore(startAt)) {
            throw new HelpRequestException(HelpRequestErrorType.INVALID_STATUS, "식사 시작 후 철회, helpRequestId=" + id);
        }
        cancel(RequestCancelType.STUDENT_WITHDRAW, now);
    }

    public boolean isRequestedBy(Long studentId) {
        return student.getAccountId().equals(studentId);
    }

    public void cancelByStudent(LocalDateTime now) {
        requireStatus(HelpRequestStatus.MATCHED);
        cancel(RequestCancelType.STUDENT_CANCEL, now);
    }

    public void cancelByDeactivation(LocalDateTime now) {
        requireStatus(HelpRequestStatus.RECRUITING, HelpRequestStatus.MATCHED);
        cancel(RequestCancelType.ACCOUNT_DEACTIVATED, now);
    }

    public void complete(LocalDateTime now) {
        requireStatus(HelpRequestStatus.MATCHED);
        this.status = HelpRequestStatus.COMPLETED;
        this.completedAt = now;
    }

    public void reportNoShow(LocalDateTime now) {
        requireStatus(HelpRequestStatus.COMPLETED);
        if (now.isAfter(noShowReportDeadline())) {
            throw new HelpRequestException(HelpRequestErrorType.NO_SHOW_PERIOD_EXPIRED, "helpRequestId=" + id);
        }
        this.status = HelpRequestStatus.NO_SHOW;
        this.noShowReportedAt = now;
    }

    // 노쇼 신고 마감 = 이용 완료 + 24시간 (정각까지 신고 가능). 이용 완료 전이면 없다
    public LocalDateTime noShowReportDeadline() {
        return completedAt == null ? null : completedAt.plusHours(NO_SHOW_REPORT_HOURS);
    }

    public boolean canReportNoShow(LocalDateTime now) {
        return status == HelpRequestStatus.COMPLETED && !now.isAfter(noShowReportDeadline());
    }

    public void markFeedbackPrompted(LocalDateTime now) {
        this.feedbackPromptedAt = now;
    }

    // 이용 시간이 1시간이라 같은 슬롯이 아니라 구간이 겹치는지로 판단한다. 끝 시각 = 시작 시각은 겹치지 않는다
    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return startAt.isBefore(otherEnd) && otherStart.isBefore(endAt);
    }

    private void cancel(RequestCancelType cancelType, LocalDateTime now) {
        this.status = HelpRequestStatus.CANCELED;
        this.cancelType = cancelType;
        this.canceledAt = now;
    }

    private void requireStatus(HelpRequestStatus... allowed) {
        if (!Arrays.asList(allowed).contains(status)) {
            throw new HelpRequestException(HelpRequestErrorType.INVALID_STATUS,
                    "helpRequestId=" + id + ", status=" + status);
        }
    }
}
