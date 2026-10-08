package com.hankkiatti.domain.sms.event;

import com.hankkiatti.domain.sms.entity.SmsType;

/**
 * 재시도를 다 쓰고 최종 실패했다. 발송 결과가 필요한 기능이 구독한다.
 */
public record SmsFailedEvent(Long outboxId, SmsType smsType, Long referenceId) {}
