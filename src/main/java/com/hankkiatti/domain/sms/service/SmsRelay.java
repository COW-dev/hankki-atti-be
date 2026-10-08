package com.hankkiatti.domain.sms.service;

import com.hankkiatti.domain.sms.entity.SmsOutbox;
import com.hankkiatti.domain.sms.repository.SmsOutboxRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 아웃박스의 문자를 꺼내 보낸다. 선점(조건부 UPDATE)에 성공한 곳만 보내므로 여러 스레드·서버가 동시에 돌아도 한 번만 간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsRelay {

    private final SmsOutboxRepository smsOutboxRepository;
    private final SmsOutboxRecorder smsOutboxRecorder;
    private final SmsSender smsSender;
    private final SmsProperties smsProperties;
    private final Clock clock;

    private final AtomicBoolean notConfiguredWarned = new AtomicBoolean(false);

    /**
     * 보낼 때가 된 문자를 한 번에 batchSize만큼 보낸다.
     */
    public void dispatchDue() {
        if (!isReady()) {
            return;
        }
        LocalDateTime now = now();
        List<Long> ids = smsOutboxRepository.findDispatchableIds(
                now, staleBefore(now), smsProperties.outbox().batchSize());
        ids.forEach(this::dispatch);
    }

    /**
     * 문자 한 통을 선점해서 보낸다. 이미 다른 곳이 선점했거나 보낼 때가 아니면 아무것도 하지 않는다.
     */
    public void dispatch(Long outboxId) {
        if (!isReady()) {
            return;
        }
        LocalDateTime now = now();
        if (!smsOutboxRepository.claim(outboxId, now, staleBefore(now))) {
            return;
        }

        SmsOutbox sms = smsOutboxRepository.findById(outboxId).orElse(null);
        if (sms == null) {
            return;
        }
        try {
            smsSender.send(sms.getRecipient(), sms.getBody());
            smsOutboxRecorder.markSent(outboxId, now());
        } catch (SmsSendException e) {
            smsOutboxRecorder.markFailed(outboxId, e.getMessage(), now());
        }
    }

    private boolean isReady() {
        if (smsSender.isConfigured()) {
            return true;
        }
        if (notConfiguredWarned.compareAndSet(false, true)) {
            log.warn("sms.sns.enabled가 꺼져 있어 문자를 보내지 않습니다. 문자는 발송 대기 상태로 남아 있다가 설정 후 발송됩니다.");
        }
        return false;
    }

    private LocalDateTime staleBefore(LocalDateTime now) {
        return now.minus(smsProperties.outbox().claimTimeout());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
