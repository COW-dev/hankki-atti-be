package com.hankkiatti.domain.application.event;

/**
 * 식사 1시간 이내에 예비에서 승격돼 도우미의 응답을 기다린다. 승격 응답 요청 알림(BE-51)이 받는다. 커밋 뒤에 처리한다.
 *
 * @param reminder 식사 30분 전 재알림이면 true
 */
public record PromotionPendingEvent(Long applicationId, boolean reminder) {}
