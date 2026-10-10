package com.hankkiatti.domain.notification.service;

import com.hankkiatti.domain.notification.repository.NotificationJobRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 알림 작업을 꺼내 처리한다. 선점(조건부 UPDATE)에 성공한 곳만 처리하므로 여러 스레드·서버가 동시에 돌아도 한 번만 처리된다.
 * 처리 중 예외는 여기서 모두 받아 재시도를 예약한다 — 커밋 직후 호출한 업무 요청으로 번지지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationJobRelay {

    private final NotificationJobRepository notificationJobRepository;
    private final NotificationDispatcher dispatcher;
    private final NotificationJobProperties properties;
    private final Clock clock;

    /**
     * 처리할 때가 된 작업(놓친 작업·재시도할 작업·처리 중 서버가 죽은 작업)을 한 번에 batchSize만큼 처리한다.
     */
    public void processDue() {
        LocalDateTime now = now();
        List<Long> ids = notificationJobRepository.findProcessableIds(now, staleBefore(now), properties.batchSize());
        ids.forEach(this::process);
    }

    /**
     * 작업 하나를 선점해서 처리한다. 이미 다른 곳이 선점했거나 처리할 때가 아니면 아무것도 하지 않는다.
     */
    public void process(Long jobId) {
        try {
            LocalDateTime now = now();
            if (!notificationJobRepository.claim(jobId, now, staleBefore(now))) {
                return;
            }
            dispatcher.process(jobId, now());
        } catch (RuntimeException e) {
            recordFailure(jobId, e);
        }
    }

    private void recordFailure(Long jobId, RuntimeException cause) {
        try {
            dispatcher.markFailed(jobId, cause.getMessage(), now());
        } catch (RuntimeException e) {
            // 실패 기록도 못 했으면 선점 만료(claimTimeout) 뒤 폴러가 다시 처리한다
            log.error("알림 작업 실패 기록 실패: jobId={}", jobId, e);
        }
    }

    private LocalDateTime staleBefore(LocalDateTime now) {
        return now.minus(properties.claimTimeout());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
