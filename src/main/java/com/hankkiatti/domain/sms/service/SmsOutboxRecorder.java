package com.hankkiatti.domain.sms.service;

import com.hankkiatti.domain.sms.entity.SmsOutbox;
import com.hankkiatti.domain.sms.event.SmsFailedEvent;
import com.hankkiatti.domain.sms.event.SmsSentEvent;
import com.hankkiatti.domain.sms.repository.SmsOutboxRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송 결과를 기록한다. SNS 호출은 트랜잭션 밖에서 하고, 결과 기록만 짧은 트랜잭션으로 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsOutboxRecorder {

    private final SmsOutboxRepository smsOutboxRepository;
    private final SmsProperties smsProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void markSent(Long outboxId, LocalDateTime now) {
        SmsOutbox sms = smsOutboxRepository.getReferenceById(outboxId);
        sms.markSent(now);
        eventPublisher.publishEvent(new SmsSentEvent(sms.getId(), sms.getSmsType(), sms.getReferenceId()));
    }

    @Transactional
    public void markFailed(Long outboxId, String error, LocalDateTime now) {
        SmsOutbox sms = smsOutboxRepository.getReferenceById(outboxId);
        sms.recordFailure(error, now, smsProperties.outbox().retryDelays());

        if (sms.isFailed()) {
            log.error("문자 최종 발송 실패: id={}, type={}, attempts={}", sms.getId(), sms.getSmsType(), sms.getAttempts());
            eventPublisher.publishEvent(new SmsFailedEvent(sms.getId(), sms.getSmsType(), sms.getReferenceId()));
            return;
        }
        log.warn("문자 발송 실패, 재시도 예약: id={}, type={}, attempts={}, next={}",
                sms.getId(), sms.getSmsType(), sms.getAttempts(), sms.getNextAttemptAt());
    }
}
