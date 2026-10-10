package com.hankkiatti.domain.notification.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 인앱 알림 문장. 시각과 상태만 담는다 — 이름·장애 유형·특이사항·메모는 넣지 않는다.
 * 형식: "10월 12일(월) 12:00 식사 도우미가 매칭됐어요."
 */
final class NotificationMessages {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private NotificationMessages() {}

    static String requestMatched(LocalDateTime startAt) {
        return when(startAt) + " 식사 도우미가 매칭됐어요.";
    }

    static String helperChanged(LocalDateTime startAt) {
        return when(startAt) + " 식사 도우미가 바뀌었어요.";
    }

    static String requestReopened(LocalDateTime startAt) {
        return when(startAt) + " 신청의 도우미가 빠져 다시 모집 중이에요.";
    }

    static String requestFailed(LocalDateTime startAt) {
        return when(startAt) + " 신청에 도우미가 매칭되지 않았어요.";
    }

    static String applicationMatched(LocalDateTime startAt) {
        return when(startAt) + " 식사 도우미로 매칭됐어요.";
    }

    static String waitingRegistered(LocalDateTime startAt, int waitingOrder) {
        return when(startAt) + " 신청에 예비 " + waitingOrder + "번으로 등록됐어요.";
    }

    static String promoted(LocalDateTime startAt) {
        return when(startAt) + " 신청에 예비에서 매칭으로 승격됐어요.";
    }

    static String promotionResponseRequired(LocalDateTime startAt, LocalDateTime deadline, boolean reminder) {
        String message = when(startAt) + " 신청에 예비에서 승격됐어요. " + deadline.format(TIME) + "까지 갈 수 있는지 알려 주세요.";
        return reminder ? "다시 알려 드려요. " + message : message;
    }

    static String studentCanceled(LocalDateTime startAt) {
        return when(startAt) + " 신청이 장애학생 사정으로 취소됐어요.";
    }

    static String waitingExcluded(LocalDateTime startAt) {
        return when(startAt) + " 예비 자리가 같은 시간 다른 매칭으로 빠졌어요.";
    }

    // "10월 12일(월) 12:00"
    static String when(LocalDateTime startAt) {
        String dayOfWeek = startAt.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
        return startAt.getMonthValue() + "월 " + startAt.getDayOfMonth() + "일(" + dayOfWeek + ") " + startAt.format(TIME);
    }
}
