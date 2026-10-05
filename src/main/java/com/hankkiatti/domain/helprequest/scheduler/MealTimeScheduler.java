package com.hankkiatti.domain.helprequest.scheduler;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매분 0초에 식사 시작·종료 자동 처리를 돌린다. 식사 시작이 정각·30분이라 최대 1분 안에 반영된다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "meal.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class MealTimeScheduler {

    private final MealTimeJob mealTimeJob;
    private final Clock clock;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void run() {
        mealTimeJob.processDue(LocalDateTime.now(clock));
    }
}
