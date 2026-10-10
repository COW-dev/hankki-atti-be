package com.hankkiatti.domain.notification.service;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import com.hankkiatti.domain.application.event.PromotionPendingEvent;
import com.hankkiatti.domain.application.event.WaitingExcludedEvent;
import com.hankkiatti.domain.application.event.WaitingRegisteredEvent;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.event.HelpRequestCanceledByStudentEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestFailedEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestReopenedEvent;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.notification.entity.NotificationTargetType;
import com.hankkiatti.domain.notification.entity.NotificationType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭·취소 이벤트를 받는 사람별 인앱 알림으로 바꿔 쌓는다 (요구사항 6장, Notion 기능명세서 "인앱 알림").
 * 업무 트랜잭션이 커밋된 뒤 불리므로 새 트랜잭션에서 신청·지원을 다시 읽는다 — 끝난 트랜잭션에 참여하면 저장되지 않는다.
 * 장애학생 알림은 신청으로, 도우미 알림은 그 도우미의 지원으로 이동한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final ApplicationRepository applicationRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void helperConfirmed(HelperConfirmedEvent event) {
        findApplication(event.applicationId()).ifPresent(application -> {
            HelpRequest request = application.getHelpRequest();
            switch (event.kind()) {
                case DIRECT_MATCH -> {
                    toStudent(request, NotificationType.REQUEST_MATCHED,
                            NotificationMessages.requestMatched(request.getStartAt()));
                    toHelper(application, NotificationType.APPLICATION_MATCHED,
                            NotificationMessages.applicationMatched(request.getStartAt()));
                }
                case PROMOTED -> {
                    toStudent(request, NotificationType.HELPER_CHANGED,
                            NotificationMessages.helperChanged(request.getStartAt()));
                    toHelper(application, NotificationType.PROMOTED,
                            NotificationMessages.promoted(request.getStartAt()));
                }
                // 도우미는 본인이 수락했으니 장애학생에게만
                case PROMOTION_ACCEPTED -> toStudent(request, NotificationType.HELPER_CHANGED,
                        NotificationMessages.helperChanged(request.getStartAt()));
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void promotionPending(PromotionPendingEvent event) {
        findApplication(event.applicationId()).ifPresent(application -> toHelper(application,
                NotificationType.PROMOTION_RESPONSE_REQUIRED,
                NotificationMessages.promotionResponseRequired(application.getHelpRequest().getStartAt(),
                        application.getPromotionDeadline(), event.reminder())));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void waitingRegistered(WaitingRegisteredEvent event) {
        findApplication(event.applicationId()).ifPresent(application -> toHelper(application,
                NotificationType.WAITING_REGISTERED,
                NotificationMessages.waitingRegistered(application.getHelpRequest().getStartAt(), event.waitingOrder())));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void waitingExcluded(WaitingExcludedEvent event) {
        findApplication(event.applicationId()).ifPresent(application -> toHelper(application,
                NotificationType.WAITING_EXCLUDED,
                NotificationMessages.waitingExcluded(application.getHelpRequest().getStartAt())));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestReopened(HelpRequestReopenedEvent event) {
        findRequest(event.helpRequestId()).ifPresent(request -> toStudent(request,
                NotificationType.REQUEST_REOPENED, NotificationMessages.requestReopened(request.getStartAt())));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestFailed(HelpRequestFailedEvent event) {
        notificationService.notify(event.studentAccountId(), NotificationType.REQUEST_FAILED,
                NotificationMessages.requestFailed(event.startAt()), NotificationTargetType.HELP_REQUEST,
                event.helpRequestId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void canceledByStudent(HelpRequestCanceledByStudentEvent event) {
        String message = NotificationMessages.studentCanceled(event.startAt());
        event.applicationIds().forEach(applicationId -> findApplication(applicationId)
                .ifPresent(application -> toHelper(application, NotificationType.STUDENT_CANCELED, message)));
    }

    private void toStudent(HelpRequest request, NotificationType type, String message) {
        notificationService.notify(request.getStudent().getAccountId(), type, message,
                NotificationTargetType.HELP_REQUEST, request.getId());
    }

    private void toHelper(Application application, NotificationType type, String message) {
        notificationService.notify(application.getHelper().getAccountId(), type, message,
                NotificationTargetType.APPLICATION, application.getId());
    }

    private Optional<Application> findApplication(Long applicationId) {
        Optional<Application> found = applicationRepository.findById(applicationId);
        if (found.isEmpty()) {
            log.warn("알림 대상 지원이 없음: applicationId={}", applicationId);
        }
        return found;
    }

    private Optional<HelpRequest> findRequest(Long helpRequestId) {
        Optional<HelpRequest> found = helpRequestRepository.findById(helpRequestId);
        if (found.isEmpty()) {
            log.warn("알림 대상 신청이 없음: helpRequestId={}", helpRequestId);
        }
        return found;
    }
}
