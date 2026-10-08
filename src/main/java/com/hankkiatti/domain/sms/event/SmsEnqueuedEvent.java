package com.hankkiatti.domain.sms.event;

/**
 * 문자를 아웃박스에 적었다. 커밋 직후 바로 발송을 시도하는 데 쓴다.
 */
public record SmsEnqueuedEvent(Long outboxId) {}
