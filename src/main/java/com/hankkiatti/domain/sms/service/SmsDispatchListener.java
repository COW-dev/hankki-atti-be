package com.hankkiatti.domain.sms.service;

import com.hankkiatti.domain.sms.event.SmsEnqueuedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 업무 트랜잭션이 커밋되면 문자 전용 스레드에서 바로 발송을 시도한다. 요청 스레드는 기다리지 않는다.
 * 여기서 놓친 문자(스레드 풀 포화, 서버 재시작)는 SmsOutboxPoller가 보낸다.
 */
@Component
@RequiredArgsConstructor
public class SmsDispatchListener {

    private final SmsRelay smsRelay;

    @Async("smsExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEnqueued(SmsEnqueuedEvent event) {
        smsRelay.dispatch(event.outboxId());
    }
}
