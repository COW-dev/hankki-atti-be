package com.hankkiatti.domain.notification.service;

import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import com.hankkiatti.domain.application.event.PromotionPendingEvent;
import com.hankkiatti.domain.application.event.WaitingExcludedEvent;
import com.hankkiatti.domain.application.event.WaitingRegisteredEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestCanceledByStudentEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestFailedEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestReopenedEvent;
import com.hankkiatti.domain.notification.entity.NotificationJob;
import com.hankkiatti.domain.notification.entity.NotificationJobType;
import com.hankkiatti.domain.notification.repository.NotificationJobRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 매칭·취소 이벤트를 받아 업무 트랜잭션 안에서 알림 작업을 저장한다 — 업무가 커밋되면 작업도 반드시 남고, 롤백되면 같이 사라진다.
 * 커밋 직후 같은 스레드에서 바로 처리하고, 놓치거나 실패한 작업은 NotificationJobPoller가 다시 처리한다.
 */
@Component
@RequiredArgsConstructor
public class NotificationJobRecorder {

    private final NotificationJobRepository notificationJobRepository;
    private final NotificationJobRelay notificationJobRelay;
    private final Clock clock;

    @EventListener
    public void onHelperConfirmed(HelperConfirmedEvent event) {
        record(NotificationJobType.HELPER_CONFIRMED, event.applicationId(), event.kind().name());
    }

    @EventListener
    public void onPromotionPending(PromotionPendingEvent event) {
        record(NotificationJobType.PROMOTION_PENDING, event.applicationId(), String.valueOf(event.reminder()));
    }

    @EventListener
    public void onWaitingRegistered(WaitingRegisteredEvent event) {
        record(NotificationJobType.WAITING_REGISTERED, event.applicationId(), String.valueOf(event.waitingOrder()));
    }

    @EventListener
    public void onWaitingExcluded(WaitingExcludedEvent event) {
        record(NotificationJobType.WAITING_EXCLUDED, event.applicationId(), null);
    }

    @EventListener
    public void onRequestReopened(HelpRequestReopenedEvent event) {
        record(NotificationJobType.REQUEST_REOPENED, event.helpRequestId(), null);
    }

    @EventListener
    public void onRequestFailed(HelpRequestFailedEvent event) {
        record(NotificationJobType.REQUEST_FAILED, event.helpRequestId(), null);
    }

    // 도우미마다 따로 처리·재시도되게 지원마다 작업 하나
    @EventListener
    public void onCanceledByStudent(HelpRequestCanceledByStudentEvent event) {
        event.applicationIds().forEach(applicationId ->
                record(NotificationJobType.STUDENT_CANCELED, applicationId, null));
    }

    private void record(NotificationJobType type, Long targetId, String detail) {
        Long jobId = notificationJobRepository.save(
                new NotificationJob(type, targetId, detail, LocalDateTime.now(clock))).getId();
        // 업무 트랜잭션이 커밋되면 바로 처리한다. 트랜잭션 밖에서 발행된 이벤트면 폴러가 처리한다
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationJobRelay.process(jobId);
                }
            });
        }
    }
}
