package com.hankkiatti.domain.helprequest.event;

import java.time.LocalDateTime;

/**
 * 식사 시작까지 지원자가 없어 매칭 실패로 끝났다. 학생에게 인앱·이메일 알림을 보내는 데 쓴다 (BE-51·52).
 * 트랜잭션 안에서 발행한다 — NotificationJobRecorder가 같은 트랜잭션에서 알림 작업을 저장하고(업무와 함께 커밋·롤백), 커밋 뒤에 처리한다.
 */
public record HelpRequestFailedEvent(Long helpRequestId, Long studentAccountId, LocalDateTime startAt) {}
