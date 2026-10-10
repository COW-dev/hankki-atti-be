package com.hankkiatti.domain.application.service;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.event.WaitingExcludedEvent;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확정 매칭된 도우미의 겹치는 다른 예비를 자동 제외한다 (요구사항 4.3). 매칭 트랜잭션이 커밋된 뒤 한 건씩 새 트랜잭션에서 한다.
 * 매칭 트랜잭션은 이미 "신청 → 도우미" 락을 쥐고 있어서, 거기서 다른 신청의 예비를 바꾸면 락 순서가 거꾸로 돼 교착이 생길 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverlappingWaitExclusionService {

    private final ApplicationRepository applicationRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final ApplyPolicy applyPolicy;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 제외 대상 후보: 그 도우미의 예비 중 매칭된 신청과 이용 시간이 겹치는 것. 커밋 뒤라 새 트랜잭션에서 읽는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Long> findTargets(Long helperId, Long confirmedRequestId, LocalDateTime startAt, LocalDateTime endAt) {
        return applicationRepository.findWaitingIdsOverlapping(helperId, startAt, endAt, confirmedRequestId);
    }

    /**
     * 예비 한 건을 신청 잠금 → 예비 잠금 뒤 다시 확인하고 제외한다. 그사이 예비가 아니게 됐거나(승격·빠짐),
     * 도우미가 매칭을 취소해 겹치는 확정 매칭이 없어졌으면 그대로 둔다.
     * 지원 ID로 시작하므로 도우미 취소와 같은 이유로 READ_COMMITTED이고, 잠그기 전에는 신청 ID 값만 읽는다.
     *
     * @return 제외했으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean excludeOne(Long applicationId, Long helperId) {
        Optional<Long> helpRequestId = applicationRepository.findHelpRequestIdByIdAndHelperId(applicationId, helperId);
        if (helpRequestId.isEmpty()) {
            return false;
        }
        HelpRequest request = helpRequestRepository.findByIdForUpdate(helpRequestId.get()).orElse(null);
        Application waiting = applicationRepository.findByIdForUpdate(applicationId).orElse(null);
        if (request == null || waiting == null || waiting.getStatus() != ApplicationStatus.WAITING) {
            return false;
        }
        List<Application> helperActive = applicationRepository.findActiveWithHelpRequestByHelperId(helperId);
        if (!applyPolicy.overlapsConfirmed(request, helperActive)) {
            return false;
        }
        waiting.exclude();
        eventPublisher.publishEvent(new WaitingExcludedEvent(applicationId));
        log.info("겹치는 예비 자동 제외: applicationId={}, helpRequestId={}, helperId={}",
                applicationId, request.getId(), helperId);
        return true;
    }
}
