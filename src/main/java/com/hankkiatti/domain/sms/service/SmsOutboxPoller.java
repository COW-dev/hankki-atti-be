package com.hankkiatti.domain.sms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 즉시 발송에서 빠진 문자와 재시도할 문자를 주기적으로 보낸다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sms.outbox.polling-enabled", havingValue = "true", matchIfMissing = true)
public class SmsOutboxPoller {

    private final SmsRelay smsRelay;

    @Scheduled(fixedDelayString = "${sms.outbox.poll-interval:PT10S}",
            initialDelayString = "${sms.outbox.poll-initial-delay:PT10S}")
    public void poll() {
        smsRelay.dispatchDue();
    }
}
