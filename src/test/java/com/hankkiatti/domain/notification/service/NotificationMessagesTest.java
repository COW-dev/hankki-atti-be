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
                messages.studentCanceled(NOON), messages.waitingExcluded(NOON),
                messages.noticePublished("가".repeat(100)));
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
        // 공지(NOTICE_PUBLISHED)는 식사 시각 대신 공지 제목을 채운다
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
    void 문구가없는키_키이름대신예외() {
        // 예비 자동 제외는 메일을 보내지 않아 메일 제목이 없다
        assertThatThrownBy(() -> messages.mailSubject(NotificationType.WAITING_EXCLUDED, false))
                .isInstanceOf(NoSuchMessageException.class);
    }

    @Test
    void noticePublished_제목을채움() {
        assertThat(messages.noticePublished("10월 학식당 운영 시간 안내"))
                .isEqualTo("새 공지가 올라왔어요: 10월 학식당 운영 시간 안내");
    }

    @Test
    void noticePublished_따옴표중괄호도그대로() {
        assertThat(messages.noticePublished("'아띠' {정기} 회의"))
                .isEqualTo("새 공지가 올라왔어요: '아띠' {정기} 회의");
    }

    @Test
    void noticePublished_문장이200자를넘으면_제목뒤를줄임표로() {
        // given — 제목만 200자
        String title = "가".repeat(200);

        // when
        String message = messages.noticePublished(title);

        // then
        assertThat(message).hasSize(Notification.MESSAGE_MAX_LENGTH)
                .startsWith("새 공지가 올라왔어요: 가")
                .endsWith("가…");
    }
}
