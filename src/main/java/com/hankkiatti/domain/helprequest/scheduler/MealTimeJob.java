package com.hankkiatti.domain.helprequest.scheduler;

import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.helprequest.service.MealTimeService;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 처리할 신청을 찾아 한 건씩 처리한다. 시각이 지났는데 상태가 그대로인 건을 찾는 방식이라
 * 서버가 꺼져 있던 동안 밀린 건도 다음 실행에서 함께 처리된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MealTimeJob {

    // 한 번에 처리할 최대 건수. 남은 건은 다음 실행(1분 뒤)에 처리한다
    private static final int BATCH_SIZE = 200;

    private final HelpRequestRepository helpRequestRepository;
    private final MealTimeService mealTimeService;

    public void processDue(LocalDateTime now) {
        // 시작 처리를 먼저 한다 — 꺼져 있다 켜져서 시작·종료가 모두 지난 신청도 순서대로 처리되게
        helpRequestRepository.findIdsToStart(now, BATCH_SIZE)
                .forEach(id -> runSafely("식사 시작", id, it -> mealTimeService.startMeal(it, now)));
        helpRequestRepository.findIdsToComplete(now, BATCH_SIZE)
                .forEach(id -> runSafely("식사 종료", id, it -> mealTimeService.completeMeal(it, now)));
    }

    // 한 건이 실패해도 나머지는 계속 처리한다. 실패한 건은 상태가 그대로라 다음 실행에서 다시 시도된다
    private void runSafely(String task, Long helpRequestId, Consumer<Long> action) {
        try {
            action.accept(helpRequestId);
        } catch (RuntimeException e) {
            log.error("{} 자동 처리 실패: helpRequestId={}", task, helpRequestId, e);
        }
    }
}
