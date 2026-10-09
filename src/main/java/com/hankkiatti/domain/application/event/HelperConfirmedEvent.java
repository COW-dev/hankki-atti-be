package com.hankkiatti.domain.application.event;

import java.time.LocalDateTime;

/**
 * 도우미가 신청에 확정 매칭됐다 (지원해서 바로 매칭, 예비에서 승격). 트랜잭션 안에서 발행하고 커밋 뒤에 처리한다.
 * 겹치는 다른 예비 자동 제외(BE-32)가 받고, 매칭 완료 알림(BE-51)도 받을 수 있다.
 *
 * @param startAt 매칭된 신청의 식사 시작 시각
 * @param endAt   매칭된 신청의 끝 시각 (시작 + 1시간)
 */
public record HelperConfirmedEvent(Long helperId, Long helpRequestId, LocalDateTime startAt, LocalDateTime endAt) {}
