package com.hankkiatti.domain.helprequest.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 장애학생이 매칭된 신청을 취소했다 (관리자가 장애학생 계정을 비활성화해 진행 중 신청이 취소된 경우도 같다). 학생 사정 취소가 된 지원(매칭·승격 응답 대기·예비)의 도우미 모두에게 "학생 취소" 알림(BE-51)을
 * 보내는 데 쓴다. 알림을 누르면 각자의 지원으로 가므로 지원 ID를 담는다.
 * 트랜잭션 안에서 발행한다 — NotificationJobRecorder가 같은 트랜잭션에서 알림 작업을 저장하고(업무와 함께 커밋·롤백), 커밋 뒤에 처리한다.
 */
public record HelpRequestCanceledByStudentEvent(Long helpRequestId, LocalDateTime startAt, List<Long> applicationIds) {}
