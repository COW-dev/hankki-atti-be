package com.hankkiatti.domain.application.service;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 매칭된 도우미가 빠진 신청에 다음 예비를 승격하거나 모집을 다시 연다 (요구사항 4.5).
 * 도우미 취소·승격 거절·승격 응답 마감이 같이 쓴다. 부르는 쪽이 신청 행을 잠근 트랜잭션 안에서 부른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingPromoter {

    // 식사까지 이 시간 이내에 승격되면 도우미의 응답을 받는다
    private static final long RESPONSE_REQUIRED_HOURS = 1;
    // 응답 마감은 식사 이 시간 전. 그보다 늦게 승격되면 식사 시작까지 기다린다 (2026-10-10 결정)
    private static final long RESPONSE_CUTOFF_MINUTES = 15;

    private final ApplicationRepository applicationRepository;
    private final HelperRepository helperRepository;
    private final ApplyPolicy applyPolicy;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 예비를 지원 순으로 보고, 다른 신청의 확정 매칭과 겹치지 않는 첫 도우미를 승격한다. 겹치는 예비는 자동 제외한다 —
     * 예비끼리는 겹쳐도 지원할 수 있어서 예비로 있는 사이 다른 건에 매칭된 도우미가 있을 수 있다.
     * 승격할 사람이 없으면 모집을 다시 연다. 락 순서: (신청 — 부르는 쪽) → 예비 → 승격 후보 도우미.
     */
    public ApplicationAfterAction promoteOrReopen(HelpRequest request, LocalDateTime now) {
        List<Application> waiting = applicationRepository.findWaitingForUpdate(request.getId());
        for (Application candidate : waiting) {
            Long candidateId = candidate.getHelper().getAccountId();
            // 후보가 동시에 겹치는 다른 신청에 지원하는 것과 한 줄로 서도록 도우미 행을 잠근 뒤 확인한다
            helperRepository.findByIdForUpdate(candidateId);
            List<Application> candidateActive = applicationRepository.findActiveWithHelpRequestByHelperId(candidateId);
            if (applyPolicy.overlapsConfirmed(request, candidateActive)) {
                candidate.exclude();
                log.info("승격 후보 자동 제외(시간 겹침): applicationId={}, helpRequestId={}, helperId={}",
                        candidate.getId(), request.getId(), candidateId);
                continue;
            }
            promote(request, candidate, now);
            return ApplicationAfterAction.PROMOTED;
        }
        request.reopen();
        return ApplicationAfterAction.REOPENED;
    }

    /**
     * 승격 응답 마감. 식사까지 1시간보다 많이 남았으면 응답이 필요 없다(null). 1시간 이내면 식사 15분 전,
     * 그보다 늦게 승격되면 식사 시작이다 — 식사 시작 처리가 그때까지 응답이 없으면 매칭 실패로 끝낸다.
     */
    static LocalDateTime responseDeadline(LocalDateTime startAt, LocalDateTime now) {
        if (now.isBefore(startAt.minusHours(RESPONSE_REQUIRED_HOURS))) {
            return null;
        }
        LocalDateTime cutoff = startAt.minusMinutes(RESPONSE_CUTOFF_MINUTES);
        return now.isBefore(cutoff) ? cutoff : startAt;
    }

    private void promote(HelpRequest request, Application candidate, LocalDateTime now) {
        LocalDateTime deadline = responseDeadline(request.getStartAt(), now);
        candidate.promote(now, deadline);
        request.changeHelper();
        if (deadline != null) {
            // 겹치는 다른 예비는 수락할 때 제외한다 — 거절하면 그 예비가 그대로 남아야 해서
            log.info("예비 승격(응답 대기): applicationId={}, helpRequestId={}, deadline={}",
                    candidate.getId(), request.getId(), deadline);
            return;
        }
        log.info("예비 승격: applicationId={}, helpRequestId={}", candidate.getId(), request.getId());
        // 커밋 뒤 승격된 도우미의 겹치는 다른 예비를 자동 제외한다 (BE-32)
        eventPublisher.publishEvent(new HelperConfirmedEvent(candidate.getHelper().getAccountId(), request.getId(),
                request.getStartAt(), request.getEndAt()));
    }
}
