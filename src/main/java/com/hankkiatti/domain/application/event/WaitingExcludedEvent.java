package com.hankkiatti.domain.application.event;

/**
 * 같은 시간대 다른 신청에 확정 매칭돼 예비 자리가 자동 제외됐다. 도우미 알림(BE-51)이 받는다. 커밋 뒤에 처리한다.
 */
public record WaitingExcludedEvent(Long applicationId) {}
