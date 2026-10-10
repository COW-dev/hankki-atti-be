package com.hankkiatti.domain.notification.entity;

import com.hankkiatti.domain.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 작업. 업무 트랜잭션 안에서 저장하므로 업무가 커밋되면 반드시 남고, 롤백되면 같이 사라진다.
 * 커밋 직후 바로 처리하고, 놓치거나 실패한 작업은 폴러가 다시 처리한다 (메일 아웃박스와 같은 방식).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_jobs",
        indexes = @Index(name = "idx_notification_jobs_process", columnList = "status, next_attempt_at"))
public class NotificationJob extends BaseTimeEntity {

    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private NotificationJobType type;

    // 지원 ID 또는 신청 ID (종류마다 다르다)
    @Column(nullable = false)
    private Long targetId;

    // 종류별 값 하나 (확정 종류·재알림 여부·예비 순번). 없으면 null
    @Column(length = 30)
    private String detail;

    @Column(nullable = false, length = 20)
    private NotificationJobStatus status;

    // 실패한 횟수
    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    // PROCESSING으로 선점한 시각. 오래되면 처리 중 서버가 죽은 것으로 보고 다시 선점한다
    private LocalDateTime claimedAt;

    private LocalDateTime processedAt;

    @Column(length = MAX_ERROR_LENGTH)
    private String lastError;

    public NotificationJob(NotificationJobType type, Long targetId, String detail, LocalDateTime now) {
        this.type = type;
        this.targetId = targetId;
        this.detail = detail;
        this.status = NotificationJobStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
    }

    public void markDone(LocalDateTime now) {
        this.status = NotificationJobStatus.DONE;
        this.processedAt = now;
        this.lastError = null;
    }

    /**
     * 실패를 기록한다. 재시도 간격이 남아 있으면 그만큼 뒤로 다시 예약하고, 다 쓰면 FAILED로 끝낸다.
     */
    public void recordFailure(String error, LocalDateTime now, List<Duration> retryDelays) {
        this.attempts++;
        this.lastError = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        this.claimedAt = null;
        if (attempts > retryDelays.size()) {
            this.status = NotificationJobStatus.FAILED;
            return;
        }
        this.status = NotificationJobStatus.PENDING;
        this.nextAttemptAt = now.plus(retryDelays.get(attempts - 1));
    }

    public boolean isFailed() {
        return status == NotificationJobStatus.FAILED;
    }
}
