package com.hankkiatti.domain.helprequest.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 장애학생이 매칭된 신청을 취소했다. 매칭·예비 도우미 모두에게 "학생 취소" 알림(인앱·이메일·문자)을 보내는 데 쓴다 (BE-51).
 * 트랜잭션 안에서 발행되므로 구독하는 쪽은 커밋 후(AFTER_COMMIT)에 처리한다.
 */
public record HelpRequestCanceledByStudentEvent(Long helpRequestId, LocalDateTime startAt, List<Long> helperIds) {}
