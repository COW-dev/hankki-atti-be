package com.hankkiatti.domain.mail.service;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 즉시 발송에서 빠진 메일과 재시도할 메일을 주기적으로 보낸다.
 * 스케줄러 스레드에서는 보낼 대상만 찾고, 발송은 메일 전용 스레드 풀(mailExecutor)에 넘긴다 —
 * SMTP가 느려져도 스케줄러 스레드(식사 시각 처리·알림 작업 등과 공유)가 묶이지 않게.
 * 풀이 밀려 있으면 넘긴 건이 버려지거나 다음 주기에 다시 넘어가는데, 선점(조건부 UPDATE)으로 한 번만 보내므로 괜찮다.
 */
@Component
@ConditionalOnProperty(name = "mail.outbox.polling-enabled", havingValue = "true", matchIfMissing = true)
public class MailOutboxPoller {

    private final MailRelay mailRelay;
    private final Executor mailExecutor;

    public MailOutboxPoller(MailRelay mailRelay, @Qualifier("mailExecutor") Executor mailExecutor) {
        this.mailRelay = mailRelay;
        this.mailExecutor = mailExecutor;
    }

    @Scheduled(fixedDelayString = "${mail.outbox.poll-interval:PT10S}",
            initialDelayString = "${mail.outbox.poll-initial-delay:PT10S}")
    public void poll() {
        for (Long outboxId : mailRelay.findDueIds()) {
            mailExecutor.execute(() -> mailRelay.dispatchSafely(outboxId));
        }
    }
}
