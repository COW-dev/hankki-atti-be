package com.hankkiatti.domain.application.service;

import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 확정 매칭이 커밋되면 겹치는 다른 예비를 한 건씩 제외한다. 요청 스레드에서 커밋 직후 돌아서 응답이 나가기 전에 반영된다.
 * 한 건이 실패해도 나머지는 계속한다 — 놓친 건은 그 신청에서 승격할 때 후보 확인(HelperCancelService)이 같은 규칙으로 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverlappingWaitExcluder {

    private final OverlappingWaitExclusionService exclusionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHelperConfirmed(HelperConfirmedEvent event) {
        List<Long> targets = exclusionService.findTargets(event.helperId(), event.helpRequestId(),
                event.startAt(), event.endAt());
        for (Long applicationId : targets) {
            try {
                exclusionService.excludeOne(applicationId, event.helperId());
            } catch (RuntimeException e) {
                log.warn("겹치는 예비 자동 제외 실패: applicationId={}, helperId={}", applicationId, event.helperId(), e);
            }
        }
    }
}
