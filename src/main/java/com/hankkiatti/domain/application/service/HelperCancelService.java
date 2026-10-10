package com.hankkiatti.domain.application.service;

import com.hankkiatti.domain.application.dto.request.HelperCancelRequestDto;
import com.hankkiatti.domain.application.dto.response.HelperCancelResponseDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 도우미 매칭 취소 (요구사항 4.4·4.5). 사유를 남기고, 예비가 있으면 지원 순으로 1번을 승격, 없으면 모집 재개.
 * 언제든 취소할 수 있지만 식사가 시작된 뒤에는 막는다 — 예비가 이미 종료돼 승격할 사람이 없다 (2026-10-09 결정).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelperCancelService {

    private final ApplicationRepository applicationRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final WaitingPromoter waitingPromoter;
    private final Clock clock;

    /**
     * 락 순서: 신청 → 지원(취소할 지원·예비) → 승격 후보 도우미. 지원·자동 처리와 같은 순서라 교착이 없다.
     * 지원을 먼저 읽어야 신청을 알 수 있어서, MySQL 기본(REPEATABLE READ)이면 그 첫 조회가 스냅샷이 되어 잠근 뒤의
     * 일반 조회가 오래된 데이터를 볼 수 있다. 이 트랜잭션은 READ_COMMITTED로 두어 잠근 뒤 조회가 항상 최신 커밋을 보게 한다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HelperCancelResponseDto cancel(Long helperId, Long applicationId, HelperCancelRequestDto requestDto) {
        Long helpRequestId = applicationRepository.findHelpRequestIdByIdAndHelperId(applicationId, helperId)
                .orElseThrow(() -> notFound(applicationId, helperId));
        HelpRequest request = helpRequestRepository.findByIdForUpdate(helpRequestId)
                .orElseThrow(() -> notFound(applicationId, helperId));
        Application application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> notFound(applicationId, helperId));

        if (application.getStatus() != ApplicationStatus.MATCHED) {
            // 승격 응답 대기의 거절은 승격 응답 API(PromotionResponseService)로 한다
            throw new ApplicationException(ApplicationErrorType.INVALID_STATUS,
                    "applicationId=" + applicationId + ", status=" + application.getStatus());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(request.getStartAt())) {
            throw new ApplicationException(ApplicationErrorType.MEAL_STARTED, "applicationId=" + applicationId);
        }

        // 기타가 아니면 내용은 저장하지 않는다
        String detail = requestDto.reason() == CancelReason.OTHER && StringUtils.hasText(requestDto.reasonDetail())
                ? requestDto.reasonDetail().trim()
                : null;
        application.cancelByHelper(requestDto.reason(), detail, now);
        ApplicationAfterAction afterAction = waitingPromoter.promoteOrReopen(request, now);
        application.recordAfterAction(afterAction);

        // 기타 내용은 로그에 남기지 않는다
        log.info("도우미 매칭 취소: applicationId={}, helpRequestId={}, helperId={}, reason={}, after={}",
                applicationId, helpRequestId, helperId, requestDto.reason(), afterAction);
        return new HelperCancelResponseDto(application.getId(), request.getId(), application.getStatus(),
                request.getStartAt(), request.getEndAt(), request.getHelpTypes().stream().sorted().toList(),
                application.getCancelReason(), application.getCanceledAt());
    }

    private static ApplicationException notFound(Long applicationId, Long helperId) {
        return new ApplicationException(ApplicationErrorType.NOT_FOUND,
                "applicationId=" + applicationId + ", helperId=" + helperId);
    }
}
