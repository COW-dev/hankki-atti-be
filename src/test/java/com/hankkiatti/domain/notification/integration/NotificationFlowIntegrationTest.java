package com.hankkiatti.domain.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.dto.request.HelperCancelRequestDto;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.ApplicationService;
import com.hankkiatti.domain.application.service.HelperCancelService;
import com.hankkiatti.domain.application.service.PromotionResponseService;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.helprequest.service.HelpRequestService;
import com.hankkiatti.domain.helprequest.service.MealTimeService;
import com.hankkiatti.domain.mail.entity.MailOutbox;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.repository.MailOutboxRepository;
import com.hankkiatti.domain.notification.entity.Notification;
import com.hankkiatti.domain.notification.entity.NotificationTargetType;
import com.hankkiatti.domain.notification.entity.NotificationType;
import com.hankkiatti.domain.notification.repository.NotificationJobRepository;
import com.hankkiatti.domain.notification.repository.NotificationRepository;
import com.hankkiatti.domain.sms.entity.SmsOutbox;
import com.hankkiatti.domain.sms.entity.SmsType;
import com.hankkiatti.domain.sms.repository.SmsOutboxRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 매칭·취소가 실제로 커밋되면 받는 사람별 인앱 알림이 쌓이는지 확인한다 (커밋 뒤 이벤트 → 새 트랜잭션).
 * 테스트마다 커밋하므로 끝나면 지운다.
 */
@SpringBootTest
class NotificationFlowIntegrationTest {

    private static final HelperCancelRequestDto ILLNESS = new HelperCancelRequestDto(CancelReason.ILLNESS, null);

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private HelperCancelService helperCancelService;

    @Autowired
    private PromotionResponseService promotionResponseService;

    @Autowired
    private HelpRequestService helpRequestService;

    @Autowired
    private MealTimeService mealTimeService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationJobRepository notificationJobRepository;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private SmsOutboxRepository smsOutboxRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private HelperRepository helperRepository;

    @Autowired
    private HelpRequestRepository helpRequestRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private Clock clock;

    private final List<Long> accountIds = new ArrayList<>();
    private final List<Long> helperIds = new ArrayList<>();
    private Student student;
    private Long studentId;
    private LocalDateTime tomorrowNoon;

    @BeforeEach
    void setUp() {
        Account studentAccount = accountRepository.save(new Account("60239992", "hash", AccountRole.STUDENT, false, false));
        accountIds.add(studentAccount.getId());
        student = studentRepository.save(TestProfiles.student(studentAccount));
        studentId = student.getAccountId();
        for (int i = 0; i < 3; i++) {
            Account account = accountRepository.save(
                    new Account(helperEmail(i), "hash", AccountRole.HELPER, false, false));
            accountIds.add(account.getId());
            helperIds.add(helperRepository.save(TestProfiles.helper(account, "6025000" + i)).getAccountId());
        }
        tomorrowNoon = LocalDateTime.now(clock).plusDays(1).truncatedTo(ChronoUnit.DAYS).withHour(12);
    }

    @AfterEach
    void tearDown() {
        notificationJobRepository.deleteAll();
        notificationRepository.deleteAll();
        mailOutboxRepository.deleteAll();
        smsOutboxRepository.deleteAll();
        applicationRepository.deleteAll();
        helpRequestRepository.deleteAll();
        helperIds.forEach(helperRepository::deleteById);
        studentRepository.deleteById(studentId);
        accountIds.forEach(accountRepository::deleteById);
    }

    private Long saveRequest(LocalDateTime startAt) {
        return helpRequestRepository.save(new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, null))
                .getId();
    }

    private Long apply(int helperIndex, Long requestId) {
        return applicationService.apply(helperIds.get(helperIndex), requestId).applicationId();
    }

    private List<Notification> notificationsOf(Long recipientId) {
        List<Notification> newestFirst = notificationRepository.findPage(recipientId, null, 50);
        return newestFirst.reversed();
    }

    private static String helperEmail(int index) {
        return "flow-helper" + index + "@mju.ac.kr";
    }

    // 받는 주소로 쌓인 메일 종류 (적재 순)
    private List<MailType> mailsTo(String email) {
        return mailOutboxRepository.findAll().stream()
                .filter(mail -> mail.getRecipient().equals(email))
                .sorted(Comparator.comparing(MailOutbox::getId))
                .map(MailOutbox::getMailType).toList();
    }

    // 알림 이동 대상(신청·지원) ID로 쌓인 문자 종류 — 테스트 프로필은 전화번호가 같아 대상 ID로 구분한다
    private List<SmsType> smsFor(Long referenceId) {
        return smsOutboxRepository.findAll().stream()
                .filter(sms -> referenceId.equals(sms.getReferenceId()))
                .sorted(Comparator.comparing(SmsOutbox::getId))
                .map(SmsOutbox::getSmsType).toList();
    }

    private List<NotificationType> typesOf(Long recipientId) {
        return notificationsOf(recipientId).stream().map(Notification::getType).toList();
    }

    @Test
    void 지원_바로매칭은장애학생과도우미에게_예비는예비등록() {
        // given
        Long requestId = saveRequest(tomorrowNoon);

        // when
        Long matched = apply(0, requestId);
        Long waiting = apply(1, requestId);

        // then — 장애학생 알림은 신청으로, 도우미 알림은 각자의 지원으로 이동한다
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Notification toStudent = notificationsOf(studentId).getFirst();
            assertThat(toStudent.getType()).isEqualTo(NotificationType.REQUEST_MATCHED);
            assertThat(toStudent.getTargetType()).isEqualTo(NotificationTargetType.HELP_REQUEST);
            assertThat(toStudent.getTargetId()).isEqualTo(requestId);
            assertThat(typesOf(studentId)).containsExactly(NotificationType.REQUEST_MATCHED);
            Notification toMatched = notificationsOf(helperIds.get(0)).getFirst();
            assertThat(toMatched.getType()).isEqualTo(NotificationType.APPLICATION_MATCHED);
            assertThat(toMatched.getTargetId()).isEqualTo(matched);
            Notification toWaiting = notificationsOf(helperIds.get(1)).getFirst();
            assertThat(toWaiting.getType()).isEqualTo(NotificationType.WAITING_REGISTERED);
            assertThat(toWaiting.getTargetId()).isEqualTo(waiting);
            assertThat(toWaiting.getMessage()).contains("예비 1번");
            // 급한 알림(매칭 완료)만 메일·문자도 — 예비 등록은 인앱만
            assertThat(mailsTo("60239992@mju.ac.kr")).containsExactly(MailType.MATCHED);
            assertThat(mailsTo(helperEmail(0))).containsExactly(MailType.MATCHED);
            assertThat(mailsTo(helperEmail(1))).isEmpty();
            assertThat(smsFor(requestId)).containsExactly(SmsType.MATCHED);
            assertThat(smsFor(matched)).containsExactly(SmsType.MATCHED);
            assertThat(smsFor(waiting)).isEmpty();
        });
    }

    @Test
    void 도우미취소_예비승격이면도우미바뀜과승격_예비없으면다시모집중() {
        // given
        Long requestId = saveRequest(tomorrowNoon);
        Long matched = apply(0, requestId);
        Long waiting = apply(1, requestId);

        // when — 도우미0 취소 → 도우미1 승격, 이어서 도우미1 취소 → 예비 없음
        helperCancelService.cancel(helperIds.get(0), matched, ILLNESS);
        helperCancelService.cancel(helperIds.get(1), waiting, ILLNESS);

        // then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(typesOf(studentId)).containsExactly(NotificationType.REQUEST_MATCHED,
                    NotificationType.HELPER_CHANGED, NotificationType.REQUEST_REOPENED);
            assertThat(typesOf(helperIds.get(1))).containsExactly(NotificationType.WAITING_REGISTERED,
                    NotificationType.PROMOTED);
            // 취소한 본인에게는 알림이 없다
            assertThat(typesOf(helperIds.get(0))).containsExactly(NotificationType.APPLICATION_MATCHED);
        });
    }

    @Test
    void 한시간이내승격_응답요청_재알림은한번_수락하면장애학생에게도우미바뀜() {
        // given — 40분 뒤 식사, 도우미0 매칭·도우미1 예비
        LocalDateTime startAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES).plusMinutes(40);
        Long requestId = saveRequest(startAt);
        Long matched = apply(0, requestId);
        Long waiting = apply(1, requestId);

        // when — 취소로 응답 대기, 30분 전 재알림을 두 서버가 한 번씩 돌린 셈, 그다음 수락
        helperCancelService.cancel(helperIds.get(0), matched, ILLNESS);
        promotionResponseService.remindUnanswered(waiting, startAt.minusMinutes(30));
        promotionResponseService.remindUnanswered(waiting, startAt.minusMinutes(29));
        promotionResponseService.accept(helperIds.get(1), waiting);

        // then — 응답 대기 동안 장애학생에게는 도우미 바뀜을 보내지 않고, 수락한 뒤에 보낸다
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Notification> toWaiting = notificationsOf(helperIds.get(1));
            assertThat(toWaiting).extracting(Notification::getType).containsExactly(
                    NotificationType.WAITING_REGISTERED,
                    NotificationType.PROMOTION_RESPONSE_REQUIRED,
                    NotificationType.PROMOTION_RESPONSE_REQUIRED);
            assertThat(toWaiting.get(2).getMessage()).startsWith("다시 알려 드려요.");
            assertThat(typesOf(studentId)).containsExactly(NotificationType.REQUEST_MATCHED,
                    NotificationType.HELPER_CHANGED);
            // 승격 응답 요청은 첫 알림·재알림 모두 메일·문자로도, 도우미 바뀜은 인앱만
            assertThat(mailsTo(helperEmail(1))).containsExactly(MailType.PROMOTED, MailType.PROMOTED);
            assertThat(smsFor(waiting)).containsExactly(SmsType.PROMOTED, SmsType.PROMOTED);
            assertThat(mailsTo("60239992@mju.ac.kr")).containsExactly(MailType.MATCHED);
        });
    }

    @Test
    void 장애학생매칭취소_매칭과예비도우미모두각자지원으로() {
        // given
        Long requestId = saveRequest(tomorrowNoon);
        Long matched = apply(0, requestId);
        Long waiting = apply(1, requestId);

        // when
        helpRequestService.cancelMatched(studentId, requestId);

        // then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(notificationsOf(helperIds.get(0)).getLast())
                    .extracting(Notification::getType, Notification::getTargetId)
                    .containsExactly(NotificationType.STUDENT_CANCELED, matched);
            assertThat(notificationsOf(helperIds.get(1)).getLast())
                    .extracting(Notification::getType, Notification::getTargetId)
                    .containsExactly(NotificationType.STUDENT_CANCELED, waiting);
            assertThat(typesOf(studentId)).containsExactly(NotificationType.REQUEST_MATCHED);
            assertThat(mailsTo(helperEmail(0))).containsExactly(MailType.MATCHED, MailType.COUNTERPART_CANCELED);
            assertThat(mailsTo(helperEmail(1))).containsExactly(MailType.COUNTERPART_CANCELED);
            assertThat(smsFor(waiting)).containsExactly(SmsType.COUNTERPART_CANCELED);
        });
    }

    @Test
    void 식사시작까지미매칭_매칭실패() {
        // given
        LocalDateTime startAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES).plusMinutes(10);
        Long requestId = saveRequest(startAt);

        // when
        mealTimeService.startMeal(requestId, startAt);

        // then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(notificationsOf(studentId)).singleElement()
                    .extracting(Notification::getType, Notification::getTargetId)
                    .containsExactly(NotificationType.REQUEST_FAILED, requestId);
            assertThat(mailsTo("60239992@mju.ac.kr")).containsExactly(MailType.MATCH_FAILED);
            assertThat(smsFor(requestId)).containsExactly(SmsType.MATCH_FAILED);
        });
    }

    @Test
    void 같은시간다른신청에바로매칭_겹치는예비는자동제외알림() {
        // given — 도우미1이 12:00 신청(도우미0 매칭)에 예비, 같은 12:00 모집 중 신청이 하나 더
        Long matchedRequest = saveRequest(tomorrowNoon);
        apply(0, matchedRequest);
        Long waiting = apply(1, matchedRequest);
        Long open = saveRequest(tomorrowNoon);

        // when
        apply(1, open);

        // then — 매칭 알림과 자동 제외 알림은 같은 이벤트를 받는 서로 다른 리스너가 쌓아 둘의 순서는 정해져 있지 않다
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Notification> toHelper = notificationsOf(helperIds.get(1));
            assertThat(toHelper).extracting(Notification::getType).containsExactlyInAnyOrder(
                    NotificationType.WAITING_REGISTERED, NotificationType.APPLICATION_MATCHED,
                    NotificationType.WAITING_EXCLUDED);
            assertThat(toHelper)
                    .filteredOn(notification -> notification.getType() == NotificationType.WAITING_EXCLUDED)
                    .singleElement().extracting(Notification::getTargetId).isEqualTo(waiting);
        });
    }

    @Test
    void 업무가실패하면_알림도없다() {
        // given
        Long requestId = saveRequest(tomorrowNoon);
        apply(0, requestId);

        // when — 같은 신청에 다시 지원 → 409
        assertThatThrownBy(() -> apply(0, requestId)).isInstanceOf(ApplicationException.class);

        // then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(typesOf(helperIds.get(0))).containsExactly(NotificationType.APPLICATION_MATCHED);
            assertThat(mailsTo(helperEmail(0))).containsExactly(MailType.MATCHED);
        });
    }
}
