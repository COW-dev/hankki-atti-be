package com.hankkiatti.domain.mail.entity;

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
 * 보낼 메일. 업무 트랜잭션 안에서 저장하고, 커밋 뒤 별도 스레드가 꺼내 보낸다.
 * 수신자·본문은 개인정보라 toString을 만들지 않고 로그에도 남기지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "mail_outbox", indexes = @Index(name = "idx_mail_outbox_dispatch", columnList = "status, next_attempt_at"))
public class MailOutbox extends BaseTimeEntity {

    private static final int MAX_ERROR_LENGTH = 500;
    // 계정정보 메일에는 초기 비밀번호가 들어가므로 발송이 끝나면 본문을 지운다
    private static final String CLEARED_BODY = "";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private MailType mailType;

    @Column(nullable = false, length = 100)
    private String recipient;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 20)
    private MailOutboxStatus status;

    // 실패한 횟수
    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    // SENDING으로 선점한 시각. 오래되면 발송 중 서버가 죽은 것으로 보고 다시 선점한다
    private LocalDateTime claimedAt;

    private LocalDateTime sentAt;

    @Column(length = MAX_ERROR_LENGTH)
    private String lastError;

    // 메일과 연결된 대상 ID (예: 계정정보 메일이면 학생 계정 ID). 발송 결과 이벤트로 넘긴다
    private Long referenceId;

    public MailOutbox(MailType mailType, String recipient, String subject, String body, Long referenceId,
                      LocalDateTime now) {
        this.mailType = mailType;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.referenceId = referenceId;
        this.status = MailOutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
    }

    public void markSent(LocalDateTime now) {
        this.status = MailOutboxStatus.SENT;
        this.sentAt = now;
        this.lastError = null;
        this.body = CLEARED_BODY;
    }

    /**
     * 실패를 기록한다. 재시도 간격이 남아 있으면 그만큼 뒤로 다시 예약하고, 다 쓰면 FAILED로 끝낸다.
     */
    public void recordFailure(String error, LocalDateTime now, List<Duration> retryDelays) {
        this.attempts++;
        this.lastError = truncate(error);
        this.claimedAt = null;

        if (attempts > retryDelays.size()) {
            this.status = MailOutboxStatus.FAILED;
            this.body = CLEARED_BODY;
            return;
        }
        this.status = MailOutboxStatus.PENDING;
        this.nextAttemptAt = now.plus(retryDelays.get(attempts - 1));
    }

    public boolean isFailed() {
        return status == MailOutboxStatus.FAILED;
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }
}
