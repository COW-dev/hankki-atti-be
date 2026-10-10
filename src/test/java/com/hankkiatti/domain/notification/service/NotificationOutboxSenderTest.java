package com.hankkiatti.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.service.MailOutboxService;
import com.hankkiatti.domain.mail.service.MailProperties;
import com.hankkiatti.domain.notification.entity.NotificationType;
import com.hankkiatti.domain.notification.service.NotificationOutboxSender.Recipient;
import com.hankkiatti.domain.sms.entity.SmsType;
import com.hankkiatti.domain.sms.service.SmsOutboxService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxSenderTest {

    // 2026-10-12(월) 12:00
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 12, 12, 0);
    private static final Recipient TO = new Recipient("60231234@mju.ac.kr", "010-1234-5678");
    private static final Set<NotificationType> URGENT = EnumSet.of(NotificationType.REQUEST_MATCHED,
            NotificationType.APPLICATION_MATCHED, NotificationType.PROMOTED,
            NotificationType.PROMOTION_RESPONSE_REQUIRED, NotificationType.STUDENT_CANCELED,
            NotificationType.REQUEST_FAILED);

    @Mock
    private MailOutboxService mailOutboxService;

    @Mock
    private SmsOutboxService smsOutboxService;

    private final NotificationMessages messages = new NotificationMessages();

    private NotificationOutboxSender sender;

    @BeforeEach
    void setUp() {
        MailProperties mailProperties = new MailProperties("no-reply@test", "한끼아띠", "https://app.example.org/",
                new MailProperties.Outbox(20, List.of(Duration.ofMinutes(1)), Duration.ofMinutes(5)));
        sender = new NotificationOutboxSender(mailOutboxService, smsOutboxService, mailProperties, messages);
    }

    private void send(NotificationType type) {
        sender.send(type, TO, "인앱 문장", NOON, NOON.minusMinutes(15), false, 31L);
    }

    @Test
    void send_매칭완료_메일과문자를적재() {
        // when
        sender.send(NotificationType.REQUEST_MATCHED, TO, "10월 12일(월) 12:00 식사 도우미가 매칭됐어요.", NOON, null, false,
                10L);

        // then
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailOutboxService).enqueue(eq(MailType.MATCHED), eq("60231234@mju.ac.kr"),
                eq("[한끼아띠] 식사 도우미가 매칭됐어요"), body.capture(), eq(10L));
        assertThat(body.getValue())
                .startsWith("10월 12일(월) 12:00 식사 도우미가 매칭됐어요.\n\n")
                .contains("https://app.example.org/")
                .contains("02-300-1529");
        verify(smsOutboxService).enqueue(SmsType.MATCHED, "010-1234-5678",
                "[한끼아띠] 10월 12일(월) 12:00 식사 도우미가 매칭됐어요. 앱에서 확인해 주세요.", 10L);
    }

    @Test
    void send_종류별메일문자종류() {
        // when
        send(NotificationType.PROMOTION_RESPONSE_REQUIRED);
        send(NotificationType.STUDENT_CANCELED);
        send(NotificationType.REQUEST_FAILED);

        // then
        verify(mailOutboxService).enqueue(eq(MailType.PROMOTED), anyString(), anyString(), anyString(), anyLong());
        verify(mailOutboxService).enqueue(eq(MailType.COUNTERPART_CANCELED), anyString(), anyString(), anyString(),
                anyLong());
        verify(mailOutboxService).enqueue(eq(MailType.MATCH_FAILED), anyString(), anyString(), anyString(), anyLong());
        verify(smsOutboxService).enqueue(SmsType.PROMOTED, TO.phone(),
                "[한끼아띠] 10월 12일(월) 12:00 예비에서 승격됐어요. 11:45까지 앱에서 응답해 주세요.", 31L);
    }

    @Test
    void send_승격응답재알림_재알림제목과문자() {
        // when
        sender.send(NotificationType.PROMOTION_RESPONSE_REQUIRED, TO, "인앱 문장", NOON, NOON.minusMinutes(15), true, 31L);

        // then
        verify(mailOutboxService).enqueue(eq(MailType.PROMOTED), eq(TO.email()),
                eq("[한끼아띠] 다시 알림: 승격 응답을 기다리고 있어요"), anyString(), eq(31L));
        verify(smsOutboxService).enqueue(SmsType.PROMOTED, TO.phone(),
                "[한끼아띠] 다시 알림: 10월 12일(월) 12:00 승격 응답을 11:45까지 앱에서 해 주세요.", 31L);
    }

    @Test
    void send_급하지않은알림_인앱만() {
        // when
        for (NotificationType type : EnumSet.complementOf(EnumSet.copyOf(URGENT))) {
            send(type);
        }

        // then
        verifyNoInteractions(mailOutboxService, smsOutboxService);
    }

    @Test
    void 문자_모두대괄호머리로시작하고한통70자안_링크없음() {
        for (NotificationType type : URGENT) {
            for (boolean reminder : reminderCases(type)) {
                LocalDateTime deadline = type == NotificationType.PROMOTION_RESPONSE_REQUIRED ? NOON.minusMinutes(15) : null;
                String sms = messages.sms(type, NOON, deadline, reminder);
                assertThat(sms).as(type + " reminder=" + reminder)
                        .startsWith("[한끼아띠] ")
                        .doesNotContain("http")
                        .doesNotContainPattern("\\{\\d+}");
                assertThat(sms.length()).as(type + " length").isLessThanOrEqualTo(70);
            }
        }
    }

    @Test
    void 메일제목_모두있고자리표시자없음() {
        for (NotificationType type : URGENT) {
            for (boolean reminder : reminderCases(type)) {
                assertThat(messages.mailSubject(type, reminder)).as(type.name()).startsWith("[한끼아띠] ");
            }
        }
        assertThat(messages.mailBody("문장", "https://app.example.org/")).doesNotContainPattern("\\{\\d+}");
    }

    private static List<Boolean> reminderCases(NotificationType type) {
        return type == NotificationType.PROMOTION_RESPONSE_REQUIRED ? List.of(false, true) : List.of(false);
    }
}
