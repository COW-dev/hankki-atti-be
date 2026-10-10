package com.hankkiatti.domain.notification.service;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.notification.entity.NotificationJob;
import com.hankkiatti.domain.notification.entity.NotificationTargetType;
import com.hankkiatti.domain.notification.entity.NotificationType;
import com.hankkiatti.domain.notification.repository.NotificationJobRepository;
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
 * 알림 작업 하나를 받는 사람별 인앱 알림으로 바꿔 쌓는다 (요구사항 6장, Notion 기능명세서 "인앱 알림").
 * 새 트랜잭션에서 신청·지원을 다시 읽어 알림을 쌓고, 같은 트랜잭션에서 작업을 완료로 바꾼다 — 알림만 쌓이고 작업이 남아 다시 보내는 일이 없다.
 * 장애학생 알림은 신청으로, 도우미 알림은 그 도우미의 지원으로 이동한다. 급한 4종은 메일·문자 아웃박스에도 함께 적재한다 (BE-52).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final ApplicationRepository applicationRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final NotificationJobRepository notificationJobRepository;
    private final NotificationService notificationService;
    private final NotificationMessages messages;
    private final NotificationOutboxSender outboxSender;
    private final NotificationJobProperties jobProperties;

    /**
     * 선점한 작업을 처리하고 완료로 바꾼다. 알림 대상 신청·지원이 그사이 사라졌으면 알림 없이 완료한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long jobId, LocalDateTime now) {
        NotificationJob job = notificationJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        Long targetId = job.getTargetId();
        switch (job.getType()) {
            case HELPER_CONFIRMED -> helperConfirmed(targetId, HelperConfirmedEvent.Kind.valueOf(job.getDetail()));
            case PROMOTION_PENDING -> promotionPending(targetId, Boolean.parseBoolean(job.getDetail()));
            case WAITING_REGISTERED -> waitingRegistered(targetId, Integer.parseInt(job.getDetail()));
            case WAITING_EXCLUDED -> waitingExcluded(targetId);
            case REQUEST_REOPENED -> requestReopened(targetId);
            case REQUEST_FAILED -> requestFailed(targetId);
            case STUDENT_CANCELED -> studentCanceled(targetId);
        }
        job.markDone(now);
    }

    /**
     * 처리에 실패한 작업을 기록한다. 재시도 간격이 남아 있으면 다시 예약하고, 다 쓰면 FAILED로 남긴다 (DB에서 바로 보인다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long jobId, String error, LocalDateTime now) {
        NotificationJob job = notificationJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        job.recordFailure(error, now, jobProperties.retryDelays());
        if (job.isFailed()) {
            log.error("알림 작업 최종 실패: jobId={}, type={}, targetId={}, attempts={}",
                    jobId, job.getType(), job.getTargetId(), job.getAttempts());
            return;
        }
        log.warn("알림 작업 실패, 재시도 예약: jobId={}, type={}, attempts={}, next={}",
                jobId, job.getType(), job.getAttempts(), job.getNextAttemptAt());
    }

    private void helperConfirmed(Long applicationId, HelperConfirmedEvent.Kind kind) {
        findApplication(applicationId).ifPresent(application -> {
            HelpRequest request = application.getHelpRequest();
            switch (kind) {
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

    private void promotionPending(Long applicationId, boolean reminder) {
        findApplication(applicationId).ifPresent(application -> {
            LocalDateTime startAt = application.getHelpRequest().getStartAt();
            LocalDateTime deadline = application.getPromotionDeadline();
            toHelper(application, NotificationType.PROMOTION_RESPONSE_REQUIRED,
                    messages.promotionResponseRequired(startAt, deadline, reminder), deadline, reminder);
        });
    }

    private void waitingRegistered(Long applicationId, int waitingOrder) {
        findApplication(applicationId).ifPresent(application -> toHelper(application,
                NotificationType.WAITING_REGISTERED,
                messages.waitingRegistered(application.getHelpRequest().getStartAt(), waitingOrder)));
    }

    private void waitingExcluded(Long applicationId) {
        findApplication(applicationId).ifPresent(application -> toHelper(application,
                NotificationType.WAITING_EXCLUDED,
                messages.waitingExcluded(application.getHelpRequest().getStartAt())));
    }

    private void requestReopened(Long helpRequestId) {
        findRequest(helpRequestId).ifPresent(request -> toStudent(request,
                NotificationType.REQUEST_REOPENED, messages.requestReopened(request.getStartAt())));
    }

    private void requestFailed(Long helpRequestId) {
        findRequest(helpRequestId).ifPresent(request -> toStudent(request,
                NotificationType.REQUEST_FAILED, messages.requestFailed(request.getStartAt())));
    }

    private void studentCanceled(Long applicationId) {
        findApplication(applicationId).ifPresent(application -> toHelper(application,
                NotificationType.STUDENT_CANCELED,
                messages.studentCanceled(application.getHelpRequest().getStartAt())));
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
