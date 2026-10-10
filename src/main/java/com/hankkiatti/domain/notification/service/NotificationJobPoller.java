package com.hankkiatti.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 커밋 직후 처리에서 빠진 알림 작업(서버 재시작 등)과 재시도할 작업을 주기적으로 처리한다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.job.polling-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationJobPoller {

    private final NotificationJobRelay notificationJobRelay;

    @Scheduled(fixedDelayString = "${notification.job.poll-interval:PT10S}",
            initialDelayString = "${notification.job.poll-initial-delay:PT10S}")
    public void poll() {
        notificationJobRelay.processDue();
    }
}
