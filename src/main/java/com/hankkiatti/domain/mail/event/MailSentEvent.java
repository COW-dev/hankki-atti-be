package com.hankkiatti.domain.mail.event;

import com.hankkiatti.domain.mail.entity.MailType;

public record MailSentEvent(Long outboxId, MailType mailType, Long referenceId) {}
