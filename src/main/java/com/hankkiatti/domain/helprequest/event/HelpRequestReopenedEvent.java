package com.hankkiatti.domain.helprequest.event;

/**
 * 매칭된 도우미가 빠졌는데 승격할 예비가 없어 다시 모집 중이 됐다 (도우미 취소·승격 거절·응답 마감).
 * 장애학생 "다시 모집 중" 알림(BE-51)이 받는다. 커밋 뒤에 처리한다.
 */
public record HelpRequestReopenedEvent(Long helpRequestId) {}
