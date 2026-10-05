package com.hankkiatti.domain.mail.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 즉시 발송에서 빠진 메일과 재시도할 메일을 주기적으로 보낸다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mail.outbox.polling-enabled", havingValue = "true", matchIfMissing = true)
public class MailOutboxPoller {

    private final MailRelay mailRelay;

    @Scheduled(fixedDelayString = "${mail.outbox.poll-interval:PT10S}",
            initialDelayString = "${mail.outbox.poll-initial-delay:PT10S}")
    public void poll() {
        mailRelay.dispatchDue();
    }
}
