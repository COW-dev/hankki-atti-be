package com.hankkiatti.domain.notification.service;

import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.service.MailOutboxService;
import com.hankkiatti.domain.mail.service.MailProperties;
import com.hankkiatti.domain.notification.entity.NotificationType;
import com.hankkiatti.domain.sms.entity.SmsType;
import com.hankkiatti.domain.sms.service.SmsOutboxService;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 급한 알림 4종(매칭 완료·예비에서 승격·상대방 취소·매칭 실패)을 메일·문자 아웃박스에도 적재한다 (Notion 기능명세서 "이메일 알림",
 * 요구사항 6장 문자). 인앱 알림과 같은 트랜잭션에서 부른다 — 셋이 함께 쌓이거나 함께 없다. 실제 발송·재시도는 아웃박스가 커밋 뒤에 한다.
 * 문자는 SMS_SNS_ENABLED가 꺼져 있으면 발송 대기로만 남는다.
 */
@Component
@RequiredArgsConstructor
public class NotificationOutboxSender {

    private static final Map<NotificationType, MailType> MAIL_TYPES = Map.of(
            NotificationType.REQUEST_MATCHED, MailType.MATCHED,
            NotificationType.APPLICATION_MATCHED, MailType.MATCHED,
            NotificationType.PROMOTED, MailType.PROMOTED,
            NotificationType.PROMOTION_RESPONSE_REQUIRED, MailType.PROMOTED,
            NotificationType.STUDENT_CANCELED, MailType.COUNTERPART_CANCELED,
            NotificationType.REQUEST_FAILED, MailType.MATCH_FAILED);

    private static final Map<NotificationType, SmsType> SMS_TYPES = Map.of(
            NotificationType.REQUEST_MATCHED, SmsType.MATCHED,
            NotificationType.APPLICATION_MATCHED, SmsType.MATCHED,
            NotificationType.PROMOTED, SmsType.PROMOTED,
            NotificationType.PROMOTION_RESPONSE_REQUIRED, SmsType.PROMOTED,
            NotificationType.STUDENT_CANCELED, SmsType.COUNTERPART_CANCELED,
            NotificationType.REQUEST_FAILED, SmsType.MATCH_FAILED);

    private final MailOutboxService mailOutboxService;
    private final SmsOutboxService smsOutboxService;
    private final MailProperties mailProperties;
    private final NotificationMessages messages;

    /**
     * 메일·문자로도 보내는 알림 종류면 두 아웃박스에 적재한다. 그 밖의 종류는 아무것도 하지 않는다 (인앱만).
     *
     * @param inAppMessage 인앱 알림 문장 — 메일 본문 첫 줄로 쓴다
     * @param deadline     승격 응답 마감 (승격 응답 요청일 때만, 그 밖에는 null)
     * @param referenceId  알림 이동 대상 ID (신청·지원)
     */
    public void send(NotificationType type, Recipient to, String inAppMessage, LocalDateTime startAt,
                     LocalDateTime deadline, boolean reminder, Long referenceId) {
        MailType mailType = MAIL_TYPES.get(type);
        if (mailType == null) {
            return;
        }
        mailOutboxService.enqueue(mailType, to.email(), messages.mailSubject(type, reminder),
                messages.mailBody(inAppMessage, appLink()), referenceId);
        smsOutboxService.enqueue(SMS_TYPES.get(type), to.phone(),
                messages.sms(type, startAt, deadline, reminder), referenceId);
    }

    // 앱 첫 화면 — 역할별 화면은 앱이 나눈다
    private String appLink() {
        return StringUtils.trimTrailingCharacter(mailProperties.linkBaseUrl(), '/') + "/";
    }

    /**
     * 받는 사람 연락처. 장애학생은 학교 이메일·등록 전화번호, 도우미는 가입 이메일·전화번호 (요구사항 6장).
     */
    public record Recipient(String email, String phone) {}
}
