package com.hankkiatti.domain.sms.service;

import com.hankkiatti.domain.common.PhoneNumbers;
import com.hankkiatti.domain.sms.entity.SmsOutbox;
import com.hankkiatti.domain.sms.entity.SmsType;
import com.hankkiatti.domain.sms.event.SmsEnqueuedEvent;
import com.hankkiatti.domain.sms.repository.SmsOutboxRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문자를 보내려면 이 서비스로 아웃박스에 적는다. SNS를 직접 부르지 않는다.
 * 호출한 업무 트랜잭션에 참여하므로, 업무가 롤백되면 문자도 보내지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SmsOutboxService {

    private final SmsOutboxRepository smsOutboxRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * @param phone 저장된 전화번호 형식(010-1234-5678). 국제 표준(E.164)으로 바꿔 저장한다
     */
    @Transactional
    public Long enqueue(SmsType smsType, String phone, String body, Long referenceId) {
        SmsOutbox sms = smsOutboxRepository.save(
                new SmsOutbox(smsType, PhoneNumbers.toE164(phone), body, referenceId, LocalDateTime.now(clock)));
        eventPublisher.publishEvent(new SmsEnqueuedEvent(sms.getId()));
        return sms.getId();
    }
}
