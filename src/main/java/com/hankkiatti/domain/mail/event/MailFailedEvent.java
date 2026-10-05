package com.hankkiatti.domain.mail.event;

import com.hankkiatti.domain.mail.entity.MailType;

/**
 * 재시도를 다 쓰고 최종 실패했다. 예: 학생 계정정보 메일이면 학생의 메일 상태를 "발송 실패"로 바꾼다.
 */
public record MailFailedEvent(Long outboxId, MailType mailType, Long referenceId) {}
