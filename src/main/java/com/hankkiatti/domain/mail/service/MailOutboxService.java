package com.hankkiatti.domain.mail.service;

import com.hankkiatti.domain.mail.entity.MailOutbox;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.event.MailEnqueuedEvent;
import com.hankkiatti.domain.mail.repository.MailOutboxRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메일을 보내려면 이 서비스로 아웃박스에 적는다. 직접 SMTP를 부르지 않는다.
 * 호출한 업무 트랜잭션에 참여하므로, 업무가 롤백되면 메일도 보내지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MailOutboxService {

    private final MailOutboxRepository mailOutboxRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public Long enqueue(MailType mailType, String recipient, String subject, String body, Long referenceId) {
        MailOutbox mail = mailOutboxRepository.save(
                new MailOutbox(mailType, recipient, subject, body, referenceId, LocalDateTime.now(clock)));
        eventPublisher.publishEvent(new MailEnqueuedEvent(mail.getId()));
        return mail.getId();
    }
}
