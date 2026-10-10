package com.hankkiatti.domain.notification.service;

import com.hankkiatti.domain.notification.entity.NotificationType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/**
 * 인앱 알림 문장. 문구는 messages/notification.properties에 있고(키 = 알림 종류), 여기서는 시각 형식과 자리표시자만 채운다.
 * 형식: "10월 12일(월) 12:00 식사 도우미가 매칭됐어요." — 이름·장애 유형·특이사항·메모는 넣지 않는다.
 */
@Component
public class NotificationMessages {

    private static final String BASENAME = "messages/notification";
    private static final String KEY_PREFIX = "notification.";
    private static final String REMINDER_SUFFIX = ".reminder";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ResourceBundleMessageSource source = new ResourceBundleMessageSource();

    public NotificationMessages() {
        source.setBasename(BASENAME);
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // 키가 없으면 키 이름을 문장으로 내보내지 않고 바로 예외 — 테스트에서 잡히게
        source.setUseCodeAsDefaultMessage(false);
        source.setFallbackToSystemLocale(false);
    }

    public String requestMatched(LocalDateTime startAt) {
        return format(NotificationType.REQUEST_MATCHED, startAt);
    }

    public String helperChanged(LocalDateTime startAt) {
        return format(NotificationType.HELPER_CHANGED, startAt);
    }

    public String requestReopened(LocalDateTime startAt) {
        return format(NotificationType.REQUEST_REOPENED, startAt);
    }

    public String requestFailed(LocalDateTime startAt) {
        return format(NotificationType.REQUEST_FAILED, startAt);
    }

    public String applicationMatched(LocalDateTime startAt) {
        return format(NotificationType.APPLICATION_MATCHED, startAt);
    }

    public String waitingRegistered(LocalDateTime startAt, int waitingOrder) {
        // 숫자를 그대로 넘기면 MessageFormat이 천 단위 쉼표를 붙이므로 문자열로 넘긴다
        return format(NotificationType.WAITING_REGISTERED, startAt, String.valueOf(waitingOrder));
    }

    public String promoted(LocalDateTime startAt) {
        return format(NotificationType.PROMOTED, startAt);
    }

    public String promotionResponseRequired(LocalDateTime startAt, LocalDateTime deadline, boolean reminder) {
        String message = format(NotificationType.PROMOTION_RESPONSE_REQUIRED, startAt, deadline.format(TIME));
        return reminder
                ? message(KEY_PREFIX + NotificationType.PROMOTION_RESPONSE_REQUIRED.name() + REMINDER_SUFFIX, message)
                : message;
    }

    public String studentCanceled(LocalDateTime startAt) {
        return format(NotificationType.STUDENT_CANCELED, startAt);
    }

    public String waitingExcluded(LocalDateTime startAt) {
        return format(NotificationType.WAITING_EXCLUDED, startAt);
    }

    // "10월 12일(월) 12:00"
    static String when(LocalDateTime startAt) {
        String dayOfWeek = startAt.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
        return startAt.getMonthValue() + "월 " + startAt.getDayOfMonth() + "일(" + dayOfWeek + ") " + startAt.format(TIME);
    }

    // 알림 종류의 문구에 식사 시각({0})과 나머지 자리표시자({1}~)를 채운다
    String format(NotificationType type, LocalDateTime startAt, String... rest) {
        Object[] args = new Object[rest.length + 1];
        args[0] = when(startAt);
        System.arraycopy(rest, 0, args, 1, rest.length);
        return message(KEY_PREFIX + type.name(), args);
    }

    private String message(String key, Object... args) {
        return source.getMessage(key, args, Locale.KOREAN);
    }
}
