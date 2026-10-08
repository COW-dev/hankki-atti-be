package com.hankkiatti.domain.sms.entity;

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
 * 보낼 문자. 업무 트랜잭션 안에서 저장하고, 커밋 뒤 별도 스레드가 꺼내 보낸다. 메일 아웃박스(MailOutbox)와 같은 흐름이다.
 * 수신 번호·본문은 개인정보라 toString을 만들지 않고 로그에도 남기지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "sms_outbox", indexes = @Index(name = "idx_sms_outbox_dispatch", columnList = "status, next_attempt_at"))
public class SmsOutbox extends BaseTimeEntity {

    private static final int MAX_ERROR_LENGTH = 500;
    // 본문에 이름 등이 들어가므로 발송이 끝나면 지운다
    private static final String CLEARED_BODY = "";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private SmsType smsType;

    // E.164 형식 (+821012345678)
    @Column(nullable = false, length = 20)
    private String recipient;

    @Column(nullable = false, length = 200)
    private String body;

    @Column(nullable = false, length = 20)
    private SmsOutboxStatus status;

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

    // 문자와 연결된 대상 ID (예: 매칭 문자면 신청 ID). 발송 결과 이벤트로 넘긴다
    private Long referenceId;

    public SmsOutbox(SmsType smsType, String recipient, String body, Long referenceId, LocalDateTime now) {
        this.smsType = smsType;
        this.recipient = recipient;
        this.body = body;
        this.referenceId = referenceId;
        this.status = SmsOutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
    }

    public void markSent(LocalDateTime now) {
        this.status = SmsOutboxStatus.SENT;
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
            this.status = SmsOutboxStatus.FAILED;
            this.body = CLEARED_BODY;
            return;
        }
        this.status = SmsOutboxStatus.PENDING;
        this.nextAttemptAt = now.plus(retryDelays.get(attempts - 1));
    }

    public boolean isFailed() {
        return status == SmsOutboxStatus.FAILED;
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }
}
