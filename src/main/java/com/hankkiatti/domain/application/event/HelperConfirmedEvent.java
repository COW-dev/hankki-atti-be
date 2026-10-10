package com.hankkiatti.domain.application.event;

import java.time.LocalDateTime;

/**
 * 도우미가 신청에 확정 매칭됐다 (지원해서 바로 매칭, 예비에서 바로 승격, 응답 대기 승격 수락). 트랜잭션 안에서 발행하고 커밋 뒤에 처리한다.
 * 겹치는 다른 예비 자동 제외(BE-32)와 매칭·도우미 바뀜 알림(BE-51)이 받는다.
 *
 * @param startAt 매칭된 신청의 식사 시작 시각
 * @param endAt   매칭된 신청의 끝 시각 (시작 + 1시간)
 * @param kind    어떻게 확정됐는지 — 알림 종류가 달라진다
 */
public record HelperConfirmedEvent(Long helperId, Long helpRequestId, Long applicationId,
                                   LocalDateTime startAt, LocalDateTime endAt, Kind kind) {

    public enum Kind {
        // 모집 중인 신청에 지원해 바로 매칭
        DIRECT_MATCH,
        // 매칭된 도우미가 빠져 예비가 바로 매칭으로 승격 (식사까지 1시간보다 많이 남음)
        PROMOTED,
        // 응답 대기로 승격된 도우미가 수락
        PROMOTION_ACCEPTED
    }
}
