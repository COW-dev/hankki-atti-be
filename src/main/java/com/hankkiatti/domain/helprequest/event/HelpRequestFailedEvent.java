package com.hankkiatti.domain.helprequest.event;

import java.time.LocalDateTime;

/**
 * 식사 시작까지 지원자가 없어 매칭 실패로 끝났다. 학생에게 인앱·이메일 알림을 보내는 데 쓴다 (BE-51·52).
 * 트랜잭션 안에서 발행되므로 구독하는 쪽은 커밋 후(AFTER_COMMIT)에 처리한다.
 */
public record HelpRequestFailedEvent(Long helpRequestId, Long studentAccountId, LocalDateTime startAt) {}
