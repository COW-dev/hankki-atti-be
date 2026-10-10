package com.hankkiatti.domain.notification.service;

import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import com.hankkiatti.domain.application.event.PromotionPendingEvent;
import com.hankkiatti.domain.application.event.WaitingExcludedEvent;
import com.hankkiatti.domain.application.event.WaitingRegisteredEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestCanceledByStudentEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestFailedEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestReopenedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 업무가 커밋된 뒤 이벤트를 받아 인앱 알림을 쌓는다. 업무가 롤백되면 알림도 없다.
 * 알림 저장이 실패해도 이미 끝난 업무에는 영향을 주지 않는다 — 로그만 남긴다 (상태는 화면에서 볼 수 있다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationDispatcher dispatcher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onHelperConfirmed(HelperConfirmedEvent event) {
        runSafely("매칭 확정", event.applicationId(), () -> dispatcher.helperConfirmed(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPromotionPending(PromotionPendingEvent event) {
        runSafely("승격 응답 요청", event.applicationId(), () -> dispatcher.promotionPending(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWaitingRegistered(WaitingRegisteredEvent event) {
        runSafely("예비 등록", event.applicationId(), () -> dispatcher.waitingRegistered(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWaitingExcluded(WaitingExcludedEvent event) {
        runSafely("예비 자동 제외", event.applicationId(), () -> dispatcher.waitingExcluded(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestReopened(HelpRequestReopenedEvent event) {
        runSafely("다시 모집 중", event.helpRequestId(), () -> dispatcher.requestReopened(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequestFailed(HelpRequestFailedEvent event) {
        runSafely("매칭 실패", event.helpRequestId(), () -> dispatcher.requestFailed(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCanceledByStudent(HelpRequestCanceledByStudentEvent event) {
        runSafely("학생 취소", event.helpRequestId(), () -> dispatcher.canceledByStudent(event));
    }

    private void runSafely(String what, Long id, Runnable dispatch) {
        try {
            dispatch.run();
        } catch (RuntimeException e) {
            log.error("알림 저장 실패: {}, id={}", what, id, e);
        }
    }
}
