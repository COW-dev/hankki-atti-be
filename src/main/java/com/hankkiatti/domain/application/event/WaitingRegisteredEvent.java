package com.hankkiatti.domain.application.event;

/**
 * 매칭된 신청에 지원해 예비 N번이 됐다. 도우미 "예비 N번 배정" 알림(BE-51)이 받는다. 커밋 뒤에 처리한다.
 */
public record WaitingRegisteredEvent(Long applicationId, int waitingOrder) {}
