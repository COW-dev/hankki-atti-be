package com.hankkiatti.domain.helprequest.service;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.event.HelpRequestFailedEvent;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 식사 시작·종료 시각에 따른 자동 처리. 신청 한 건씩 행을 잠그고 상태와 시각을 다시 확인한 뒤 바꾼다.
 * 사용자 요청과 겹치거나 서버 여러 대가 동시에 돌아도, 먼저 처리한 쪽의 결과를 보고 나머지는 건너뛴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealTimeService {

    private final HelpRequestRepository helpRequestRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 식사 시작: 지원자가 없는 신청은 매칭 실패, 승격되지 못한 예비는 예비 종료.
     */
    @Transactional
    public void startMeal(Long helpRequestId, LocalDateTime now) {
        HelpRequest request = helpRequestRepository.findByIdForUpdate(helpRequestId).orElse(null);
        if (request == null || request.getStartAt().isAfter(now)) {
            return;
        }

        if (request.getStatus() == HelpRequestStatus.RECRUITING) {
            request.fail();
            eventPublisher.publishEvent(new HelpRequestFailedEvent(
                    request.getId(), request.getStudent().getAccountId(), request.getStartAt()));
        }
        // 예비 종료는 알림을 보내지 않는다 (기능명세서)
        applicationRepository.findByHelpRequestIdAndStatus(helpRequestId, ApplicationStatus.WAITING)
                .forEach(Application::expire);
    }

    /**
     * 식사 종료(시작 + 1시간): 매칭 완료 → 이용 완료, 매칭된 도우미에게 봉사시간 1시간. 이때부터 24시간 노쇼 신고가 열린다.
     */
    @Transactional
    public void completeMeal(Long helpRequestId, LocalDateTime now) {
        HelpRequest request = helpRequestRepository.findByIdForUpdate(helpRequestId).orElse(null);
        if (request == null || request.getStatus() != HelpRequestStatus.MATCHED || request.getEndAt().isAfter(now)) {
            return;
        }

        request.complete(now);
        List<Application> matched =
                applicationRepository.findByHelpRequestIdAndStatus(helpRequestId, ApplicationStatus.MATCHED);
        if (matched.isEmpty()) {
            log.warn("매칭된 지원이 없는 매칭 완료 신청을 이용 완료로 처리: helpRequestId={}", helpRequestId);
        }
        matched.forEach(Application::complete);
    }
}
