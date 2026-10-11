package com.hankkiatti.domain.application.service;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 도우미 비활성화로 지원 한 건을 해제한다. 비활성화는 지원마다 이 트랜잭션을 따로 연다 — 여러 신청을 한 트랜잭션에서
 * 처리하면 앞 신청의 승격 후보 도우미를 잠근 채 다음 신청을 잠그게 되어 락 순서(신청 → 지원 → 도우미)가 거꾸로 된다.
 * 매칭 완료·승격 응답 대기는 도우미 취소처럼(사유 관리자 비활성화) 예비 승격 / 모집 재개하고, 예비는 빠진다.
 * 락 순서와 격리 수준은 도우미 취소와 같다 (지원 ID로 시작해 신청 ID를 값으로만 먼저 읽는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelperDeactivationCanceler {

    private final ApplicationRepository applicationRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final WaitingPromoter waitingPromoter;

    /**
     * @return 해제했으면 해제 전 상태. 그사이 끝났거나 식사가 시작돼 건너뛰면 비어 있다
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<ApplicationStatus> release(Long applicationId, LocalDateTime now) {
        Long helpRequestId = applicationRepository.findHelpRequestIdById(applicationId).orElse(null);
        if (helpRequestId == null) {
            return Optional.empty();
        }
        HelpRequest request = helpRequestRepository.findByIdForUpdate(helpRequestId).orElse(null);
        Application application = applicationRepository.findByIdForUpdate(applicationId).orElse(null);
        if (request == null || application == null || !application.isActive()
                || !now.isBefore(request.getStartAt())) {
            return Optional.empty();
        }

        ApplicationStatus before = application.getStatus();
        application.cancelByDeactivation(now);
        ApplicationAfterAction after = null;
        if (before != ApplicationStatus.WAITING) {
            after = waitingPromoter.promoteOrReopen(request, now);
            application.recordAfterAction(after);
        }
        log.info("관리자 비활성화로 지원 해제: applicationId={}, helpRequestId={}, before={}, after={}",
                applicationId, helpRequestId, before, after);
        return Optional.of(before);
    }
}
