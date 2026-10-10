package com.hankkiatti.domain.notification.service;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import com.hankkiatti.domain.application.event.PromotionPendingEvent;
import com.hankkiatti.domain.application.event.WaitingExcludedEvent;
import com.hankkiatti.domain.application.event.WaitingRegisteredEvent;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.event.HelpRequestCanceledByStudentEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestFailedEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestReopenedEvent;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.notification.entity.NotificationTargetType;
import com.hankkiatti.domain.notification.entity.NotificationType;
import com.hankkiatti.domain.notification.service.NotificationOutboxSender.Recipient;
import com.hankkiatti.domain.student.entity.Student;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭·취소 이벤트를 받는 사람별 인앱 알림으로 바꿔 쌓는다 (요구사항 6장, Notion 기능명세서 "인앱 알림").
 * 업무 트랜잭션이 커밋된 뒤 불리므로 새 트랜잭션에서 신청·지원을 다시 읽는다 — 끝난 트랜잭션에 참여하면 저장되지 않는다.
 * 장애학생 알림은 신청으로, 도우미 알림은 그 도우미의 지원으로 이동한다. 급한 4종은 메일·문자 아웃박스에도 함께 적재한다 (BE-52).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final ApplicationRepository applicationRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final NotificationService notificationService;
    private final NotificationMessages messages;
    private final NotificationOutboxSender outboxSender;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void helperConfirmed(HelperConfirmedEvent event) {
        findApplication(event.applicationId()).ifPresent(application -> {
            HelpRequest request = application.getHelpRequest();
            switch (event.kind()) {
                case DIRECT_MATCH -> {
                    toStudent(request, NotificationType.REQUEST_MATCHED,
                            messages.requestMatched(request.getStartAt()));
                    toHelper(application, NotificationType.APPLICATION_MATCHED,
                            messages.applicationMatched(request.getStartAt()));
                }
                case PROMOTED -> {
                    toStudent(request, NotificationType.HELPER_CHANGED,
                            messages.helperChanged(request.getStartAt()));
                    toHelper(application, NotificationType.PROMOTED,
                            messages.promoted(request.getStartAt()));
                }
                // 도우미는 본인이 수락했으니 장애학생에게만
                case PROMOTION_ACCEPTED -> toStudent(request, NotificationType.HELPER_CHANGED,
                        messages.helperChanged(request.getStartAt()));
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void promotionPending(PromotionPendingEvent event) {
        findApplication(event.applicationId()).ifPresent(application -> {
            LocalDateTime startAt = application.getHelpRequest().getStartAt();
            LocalDateTime deadline = application.getPromotionDeadline();
            toHelper(application, NotificationType.PROMOTION_RESPONSE_REQUIRED,
                    messages.promotionResponseRequired(startAt, deadline, event.reminder()), deadline, event.reminder());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void waitingRegistered(WaitingRegisteredEvent event) {
        findApplication(event.applicationId()).ifPresent(application -> toHelper(application,
                NotificationType.WAITING_REGISTERED,
                messages.waitingRegistered(application.getHelpRequest().getStartAt(), event.waitingOrder())));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void waitingExcluded(WaitingExcludedEvent event) {
        findApplication(event.applicationId()).ifPresent(application -> toHelper(application,
                NotificationType.WAITING_EXCLUDED,
                messages.waitingExcluded(application.getHelpRequest().getStartAt())));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestReopened(HelpRequestReopenedEvent event) {
        findRequest(event.helpRequestId()).ifPresent(request -> toStudent(request,
                NotificationType.REQUEST_REOPENED, messages.requestReopened(request.getStartAt())));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestFailed(HelpRequestFailedEvent event) {
        findRequest(event.helpRequestId()).ifPresent(request -> toStudent(request,
                NotificationType.REQUEST_FAILED, messages.requestFailed(request.getStartAt())));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void canceledByStudent(HelpRequestCanceledByStudentEvent event) {
        String message = messages.studentCanceled(event.startAt());
        event.applicationIds().forEach(applicationId -> findApplication(applicationId)
                .ifPresent(application -> toHelper(application, NotificationType.STUDENT_CANCELED, message)));
    }

    // 장애학생 알림 → 신청. 급한 4종이면 학교 이메일·등록 전화번호로 메일·문자도
    private void toStudent(HelpRequest request, NotificationType type, String message) {
        Student student = request.getStudent();
        notificationService.notify(student.getAccountId(), type, message,
                NotificationTargetType.HELP_REQUEST, request.getId());
        outboxSender.send(type, new Recipient(student.getSchoolEmail(), student.getPhone()), message,
                request.getStartAt(), null, false, request.getId());
    }

    private void toHelper(Application application, NotificationType type, String message) {
        toHelper(application, type, message, null, false);
    }

    // 도우미 알림 → 그 도우미의 지원. 급한 4종이면 가입 이메일·전화번호로 메일·문자도
    private void toHelper(Application application, NotificationType type, String message,
                          LocalDateTime deadline, boolean reminder) {
        Helper helper = application.getHelper();
        notificationService.notify(helper.getAccountId(), type, message,
                NotificationTargetType.APPLICATION, application.getId());
        outboxSender.send(type, new Recipient(helper.getEmail(), helper.getPhone()), message,
                application.getHelpRequest().getStartAt(), deadline, reminder, application.getId());
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
