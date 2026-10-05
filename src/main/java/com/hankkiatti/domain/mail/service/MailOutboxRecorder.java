package com.hankkiatti.domain.mail.service;

import com.hankkiatti.domain.mail.entity.MailOutbox;
import com.hankkiatti.domain.mail.event.MailFailedEvent;
import com.hankkiatti.domain.mail.event.MailSentEvent;
import com.hankkiatti.domain.mail.repository.MailOutboxRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송 결과를 기록한다. SMTP 호출은 트랜잭션 밖에서 하고, 결과 기록만 짧은 트랜잭션으로 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailOutboxRecorder {

    private final MailOutboxRepository mailOutboxRepository;
    private final MailProperties mailProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void markSent(Long outboxId, LocalDateTime now) {
        MailOutbox mail = mailOutboxRepository.getReferenceById(outboxId);
        mail.markSent(now);
        eventPublisher.publishEvent(new MailSentEvent(mail.getId(), mail.getMailType(), mail.getReferenceId()));
    }

    @Transactional
    public void markFailed(Long outboxId, String error, LocalDateTime now) {
        MailOutbox mail = mailOutboxRepository.getReferenceById(outboxId);
        mail.recordFailure(error, now, mailProperties.outbox().retryDelays());

        if (mail.isFailed()) {
            log.error("메일 최종 발송 실패: id={}, type={}, attempts={}", mail.getId(), mail.getMailType(), mail.getAttempts());
            eventPublisher.publishEvent(new MailFailedEvent(mail.getId(), mail.getMailType(), mail.getReferenceId()));
            return;
        }
        log.warn("메일 발송 실패, 재시도 예약: id={}, type={}, attempts={}, next={}",
                mail.getId(), mail.getMailType(), mail.getAttempts(), mail.getNextAttemptAt());
    }
}
