package com.hankkiatti.domain.mail.event;

/**
 * 메일을 아웃박스에 적었다. 커밋 직후 바로 발송을 시도하는 데 쓴다.
 */
public record MailEnqueuedEvent(Long outboxId) {}
