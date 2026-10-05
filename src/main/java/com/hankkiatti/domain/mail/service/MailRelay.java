package com.hankkiatti.domain.mail.service;

import com.hankkiatti.domain.mail.entity.MailOutbox;
import com.hankkiatti.domain.mail.repository.MailOutboxRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;

/**
 * 아웃박스의 메일을 꺼내 보낸다. 선점(조건부 UPDATE)에 성공한 곳만 보내므로 여러 스레드·서버가 동시에 돌아도 한 번만 간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailRelay {

    private final MailOutboxRepository mailOutboxRepository;
    private final MailOutboxRecorder mailOutboxRecorder;
    private final SmtpMailClient smtpMailClient;
    private final MailProperties mailProperties;
    private final Clock clock;

    private final AtomicBoolean notConfiguredWarned = new AtomicBoolean(false);

    /**
     * 보낼 때가 된 메일을 한 번에 batchSize만큼 보낸다.
     */
    public void dispatchDue() {
        if (!isReady()) {
            return;
        }
        LocalDateTime now = now();
        List<Long> ids = mailOutboxRepository.findDispatchableIds(
                now, staleBefore(now), mailProperties.outbox().batchSize());
        ids.forEach(this::dispatch);
    }

    /**
     * 메일 한 통을 선점해서 보낸다. 이미 다른 곳이 선점했거나 보낼 때가 아니면 아무것도 하지 않는다.
     */
    public void dispatch(Long outboxId) {
        if (!isReady()) {
            return;
        }
        LocalDateTime now = now();
        if (!mailOutboxRepository.claim(outboxId, now, staleBefore(now))) {
            return;
        }

        MailOutbox mail = mailOutboxRepository.findById(outboxId).orElse(null);
        if (mail == null) {
            return;
        }
        try {
            smtpMailClient.send(mail.getRecipient(), mail.getSubject(), mail.getBody());
            mailOutboxRecorder.markSent(outboxId, now());
        } catch (MailException e) {
            mailOutboxRecorder.markFailed(outboxId, e.getMessage(), now());
        }
    }

    private boolean isReady() {
        if (smtpMailClient.isConfigured()) {
            return true;
        }
        if (notConfiguredWarned.compareAndSet(false, true)) {
            log.warn("spring.mail.host가 없어 메일을 보내지 않습니다. 메일은 발송 대기 상태로 남아 있다가 설정 후 발송됩니다.");
        }
        return false;
    }

    private LocalDateTime staleBefore(LocalDateTime now) {
        return now.minus(mailProperties.outbox().claimTimeout());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
