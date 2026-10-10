package com.hankkiatti.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hankkiatti.domain.notification.entity.Notification;
import com.hankkiatti.domain.notification.entity.NotificationType;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.NoSuchMessageException;

class NotificationMessagesTest {

    // 2026-10-12(월) 12:00
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 12, 12, 0);

    private final NotificationMessages messages = new NotificationMessages();

    private List<String> allMessages() {
        return List.of(
                messages.requestMatched(NOON), messages.helperChanged(NOON), messages.requestReopened(NOON),
                messages.requestFailed(NOON), messages.applicationMatched(NOON), messages.waitingRegistered(NOON, 99),
                messages.promoted(NOON), messages.promotionResponseRequired(NOON, NOON.minusMinutes(15), false),
                messages.promotionResponseRequired(NOON, NOON.minusMinutes(15), true),
                messages.studentCanceled(NOON), messages.waitingExcluded(NOON));
    }

    @Test
    void when_월일요일시각() {
        assertThat(NotificationMessages.when(NOON)).isEqualTo("10월 12일(월) 12:00");
        assertThat(NotificationMessages.when(LocalDateTime.of(2026, 10, 16, 17, 30))).isEqualTo("10월 16일(금) 17:30");
    }

    @Test
    void 문장_파일문구에시각과값을채움() {
        assertThat(messages.requestMatched(NOON)).isEqualTo("10월 12일(월) 12:00 식사 도우미가 매칭됐어요.");
        assertThat(messages.applicationMatched(NOON)).isEqualTo("10월 12일(월) 12:00 식사 도우미로 매칭됐어요.");
        assertThat(messages.waitingRegistered(NOON, 2)).isEqualTo("10월 12일(월) 12:00 신청에 예비 2번으로 등록됐어요.");
        assertThat(messages.studentCanceled(NOON)).isEqualTo("10월 12일(월) 12:00 신청이 장애학생 사정으로 취소됐어요.");
    }

    @Test
    void waitingRegistered_큰순번도쉼표없이() {
        assertThat(messages.waitingRegistered(NOON, 1000)).contains("예비 1000번");
    }

    @Test
    void promotionResponseRequired_마감시각_재알림이면앞에덧붙임() {
        assertThat(messages.promotionResponseRequired(NOON, NOON.minusMinutes(15), false))
                .isEqualTo("10월 12일(월) 12:00 신청에 예비에서 승격됐어요. 11:45까지 갈 수 있는지 알려 주세요.");
        assertThat(messages.promotionResponseRequired(NOON, NOON.minusMinutes(15), true))
                .isEqualTo("다시 알려 드려요. 10월 12일(월) 12:00 신청에 예비에서 승격됐어요. 11:45까지 갈 수 있는지 알려 주세요.");
    }

    @Test
    void 공지를뺀모든알림종류에_문구가있다() {
        // 공지(NOTICE_PUBLISHED)는 공지 기능(BE-70)에서 문구를 더한다
        Arrays.stream(NotificationType.values())
                .filter(type -> type != NotificationType.NOTICE_PUBLISHED)
                .forEach(type -> assertThat(messages.format(type, NOON, "1"))
                        .as(type.name()).startsWith("10월 12일(월) 12:00 "));
    }

    @Test
    void 모든문장_자리표시자가다채워지고_알림문장길이안() {
        assertThat(allMessages()).allSatisfy(message -> {
            assertThat(message).doesNotContainPattern("\\{\\d+}");
            assertThat(message.length()).isLessThanOrEqualTo(Notification.MESSAGE_MAX_LENGTH);
        });
    }

    @Test
    void 문구가없는종류_키이름대신예외() {
        assertThatThrownBy(() -> messages.format(NotificationType.NOTICE_PUBLISHED, NOON))
                .isInstanceOf(NoSuchMessageException.class);
    }
}
