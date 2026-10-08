package com.hankkiatti.domain.sms.event;

import com.hankkiatti.domain.sms.entity.SmsType;

public record SmsSentEvent(Long outboxId, SmsType smsType, Long referenceId) {}
