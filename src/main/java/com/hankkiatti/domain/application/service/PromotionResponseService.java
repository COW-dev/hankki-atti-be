package com.hankkiatti.domain.application.service;

import com.hankkiatti.domain.application.dto.response.MyApplicationResponseDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 식사 1시간 이내 승격의 응답 (요구사항 4.5): 도우미의 [갈게요]·[이번엔 어려워요], 응답 마감이 지난 승격의 자동 거절.
 * 거절하면 패널티 없이 다음 예비로 넘어가고, 예비가 없으면 모집을 다시 연다.
 * 모두 지원 ID로 시작하므로 도우미 취소와 같이 READ_COMMITTED로 두고, 신청 ID 값만 읽은 뒤 신청 → 지원 순으로 잠근다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionResponseService {

    private final ApplicationRepository applicationRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final WaitingPromoter waitingPromoter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 수락: 매칭 완료로 확정한다. 응답 마감이 지났으면 막는다 (자동 거절이 곧 처리한다).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MyApplicationResponseDto accept(Long helperId, Long applicationId) {
        Application application = lockMyPending(helperId, applicationId);
        HelpRequest request = application.getHelpRequest();
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(application.getPromotionDeadline())) {
            throw new ApplicationException(ApplicationErrorType.PROMOTION_EXPIRED, "applicationId=" + applicationId);
        }

        application.acceptPromotion(now);
        log.info("승격 수락: applicationId={}, helpRequestId={}, helperId={}", applicationId, request.getId(), helperId);
        // 응답 대기 동안 남겨 둔 겹치는 다른 예비를 커밋 뒤 자동 제외한다 (BE-32)
        eventPublisher.publishEvent(
                new HelperConfirmedEvent(helperId, request.getId(), request.getStartAt(), request.getEndAt()));
        return MyApplicationService.toCard(application, null);
    }

    /**
     * 거절: 승격 거절로 끝내고 다음 예비를 승격하거나 모집을 다시 연다. 마감이 지났어도 식사 시작 전이면 받는다 —
     * 자동 거절과 결과가 같다. 식사가 시작된 뒤에는 식사 시작 처리가 맡는다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MyApplicationResponseDto decline(Long helperId, Long applicationId) {
        Application application = lockMyPending(helperId, applicationId);
        HelpRequest request = application.getHelpRequest();
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(request.getStartAt())) {
            throw new ApplicationException(ApplicationErrorType.PROMOTION_EXPIRED, "applicationId=" + applicationId);
        }

        application.declinePromotion(now);
        ApplicationAfterAction after = waitingPromoter.promoteOrReopen(request, now);
        log.info("승격 거절: applicationId={}, helpRequestId={}, helperId={}, after={}",
                applicationId, request.getId(), helperId, after);
        return MyApplicationService.toCard(application, null);
    }

    /**
     * 응답 마감이 지난 승격을 거절로 처리한다 (MealTimeJob). 잠근 뒤 상태·마감을 다시 확인해, 그사이 도우미가 응답했으면 건너뛴다.
     * 마감이 식사 시작인 승격(식사 15분 전보다 늦게 승격)은 식사 시작 처리가 매칭 실패로 끝낸다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void expireUnanswered(Long applicationId, LocalDateTime now) {
        Long helpRequestId = applicationRepository.findHelpRequestIdById(applicationId).orElse(null);
        if (helpRequestId == null) {
            return;
        }
        HelpRequest request = helpRequestRepository.findByIdForUpdate(helpRequestId).orElse(null);
        Application application = applicationRepository.findByIdForUpdate(applicationId).orElse(null);
        if (request == null || application == null
                || application.getStatus() != ApplicationStatus.PROMOTION_PENDING
                || application.getPromotionDeadline().isAfter(now)
                || !now.isBefore(request.getStartAt())) {
            return;
        }

        application.declinePromotion(now);
        ApplicationAfterAction after = waitingPromoter.promoteOrReopen(request, now);
        log.info("승격 응답 마감 → 자동 거절: applicationId={}, helpRequestId={}, after={}",
                applicationId, helpRequestId, after);
    }

    // 내 승격 응답 대기를 신청 → 지원 순으로 잠가 가져온다. 없는 지원과 남의 지원은 같은 404
    private Application lockMyPending(Long helperId, Long applicationId) {
        Long helpRequestId = applicationRepository.findHelpRequestIdByIdAndHelperId(applicationId, helperId)
                .orElseThrow(() -> notFound(applicationId, helperId));
        helpRequestRepository.findByIdForUpdate(helpRequestId)
                .orElseThrow(() -> notFound(applicationId, helperId));
        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> notFound(applicationId, helperId));
        if (application.getStatus() != ApplicationStatus.PROMOTION_PENDING) {
            throw new ApplicationException(ApplicationErrorType.INVALID_STATUS,
                    "applicationId=" + applicationId + ", status=" + application.getStatus());
        }
        return application;
    }

    private static ApplicationException notFound(Long applicationId, Long helperId) {
        return new ApplicationException(ApplicationErrorType.NOT_FOUND,
                "applicationId=" + applicationId + ", helperId=" + helperId);
    }
}
