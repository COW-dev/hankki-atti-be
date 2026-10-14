package com.hankkiatti.domain.sms.service;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 즉시 발송에서 빠진 문자와 재시도할 문자를 주기적으로 보낸다.
 * 스케줄러 스레드에서는 보낼 대상만 찾고, 발송은 문자 전용 스레드 풀(smsExecutor)에 넘긴다 —
 * SNS가 느려져도 스케줄러 스레드(식사 시각 처리·알림 작업 등과 공유)가 묶이지 않게.
 * 풀이 밀려 있으면 넘긴 건이 버려지거나 다음 주기에 다시 넘어가는데, 선점(조건부 UPDATE)으로 한 번만 보내므로 괜찮다.
 */
@Component
@ConditionalOnProperty(name = "sms.outbox.polling-enabled", havingValue = "true", matchIfMissing = true)
public class SmsOutboxPoller {

    private final SmsRelay smsRelay;
    private final Executor smsExecutor;

    public SmsOutboxPoller(SmsRelay smsRelay, @Qualifier("smsExecutor") Executor smsExecutor) {
        this.smsRelay = smsRelay;
        this.smsExecutor = smsExecutor;
    }

    @Scheduled(fixedDelayString = "${sms.outbox.poll-interval:PT10S}",
            initialDelayString = "${sms.outbox.poll-initial-delay:PT10S}")
    public void poll() {
        for (Long outboxId : smsRelay.findDueIds()) {
            smsExecutor.execute(() -> smsRelay.dispatchSafely(outboxId));
        }
    }
}
