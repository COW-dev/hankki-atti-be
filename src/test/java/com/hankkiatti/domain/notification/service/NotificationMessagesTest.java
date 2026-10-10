package com.hankkiatti.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationMessagesTest {

    // 2026-10-12(월) 12:00
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 12, 12, 0);

    @Test
    void when_월일요일시각() {
        assertThat(NotificationMessages.when(NOON)).isEqualTo("10월 12일(월) 12:00");
        assertThat(NotificationMessages.when(LocalDateTime.of(2026, 10, 16, 17, 30))).isEqualTo("10월 16일(금) 17:30");
    }

    @Test
    void 문장_시각과상태만() {
        assertThat(NotificationMessages.requestMatched(NOON)).isEqualTo("10월 12일(월) 12:00 식사 도우미가 매칭됐어요.");
        assertThat(NotificationMessages.applicationMatched(NOON)).isEqualTo("10월 12일(월) 12:00 식사 도우미로 매칭됐어요.");
        assertThat(NotificationMessages.waitingRegistered(NOON, 2)).isEqualTo("10월 12일(월) 12:00 신청에 예비 2번으로 등록됐어요.");
        assertThat(NotificationMessages.studentCanceled(NOON)).isEqualTo("10월 12일(월) 12:00 신청이 장애학생 사정으로 취소됐어요.");
    }

    @Test
    void promotionResponseRequired_마감시각_재알림이면앞에덧붙임() {
        assertThat(NotificationMessages.promotionResponseRequired(NOON, NOON.minusMinutes(15), false))
                .isEqualTo("10월 12일(월) 12:00 신청에 예비에서 승격됐어요. 11:45까지 갈 수 있는지 알려 주세요.");
        assertThat(NotificationMessages.promotionResponseRequired(NOON, NOON.minusMinutes(15), true))
                .startsWith("다시 알려 드려요. 10월 12일(월) 12:00");
    }

    @Test
    void 모든문장_알림문장길이안() {
        assertThat(List.of(
                NotificationMessages.requestMatched(NOON), NotificationMessages.helperChanged(NOON),
                NotificationMessages.requestReopened(NOON), NotificationMessages.requestFailed(NOON),
                NotificationMessages.applicationMatched(NOON), NotificationMessages.waitingRegistered(NOON, 99),
                NotificationMessages.promoted(NOON),
                NotificationMessages.promotionResponseRequired(NOON, NOON.minusMinutes(15), true),
                NotificationMessages.studentCanceled(NOON), NotificationMessages.waitingExcluded(NOON)))
                .allSatisfy(message -> assertThat(message.length())
                        .isLessThanOrEqualTo(Notification.MESSAGE_MAX_LENGTH));
    }
}
