package com.hankkiatti.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.notification.entity.NotificationJob;
import com.hankkiatti.domain.notification.entity.NotificationJobStatus;
import com.hankkiatti.domain.notification.entity.NotificationJobType;
import com.hankkiatti.domain.notification.entity.NotificationTargetType;
import com.hankkiatti.domain.notification.entity.NotificationType;
import com.hankkiatti.domain.notification.repository.NotificationJobRepository;
import com.hankkiatti.domain.notification.service.NotificationOutboxSender.Recipient;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.support.TestAccounts;
import com.hankkiatti.support.TestHelpRequests;
import com.hankkiatti.support.TestProfiles;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    // 2026-10-12(월) 12:00
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 12, 12, 0);
    private static final LocalDateTime NOW = NOON.minusDays(1);
    private static final Long STUDENT_ID = 1L;
    private static final Long HELPER_ID = 7L;
    private static final Long REQUEST_ID = 10L;
    private static final Long APPLICATION_ID = 31L;
    private static final Long JOB_ID = 500L;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private NotificationJobRepository notificationJobRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationOutboxSender outboxSender;

    private NotificationDispatcher dispatcher;

    private HelpRequest request;
    private Application application;
    private Student student;
    private Helper helper;

    @BeforeEach
    void setUp() {
        NotificationJobProperties jobProperties = new NotificationJobProperties(50,
                List.of(Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofMinutes(1)), Duration.ofMinutes(5));
        dispatcher = new NotificationDispatcher(applicationRepository, helpRequestRepository, notificationJobRepository,
                notificationService, new NotificationMessages(), outboxSender, jobProperties);
        student = TestHelpRequests.student("60231234");
        ReflectionTestUtils.setField(student, "accountId", STUDENT_ID);
        request = TestHelpRequests.request(student, NOON);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        helper = TestProfiles.helper(TestAccounts.withId(HELPER_ID, AccountRole.HELPER, "hash", false), "60230001");
        ReflectionTestUtils.setField(helper, "accountId", HELPER_ID);
        application = new Application(request, helper, NOON.minusDays(1));
        ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
    }

    private NotificationJob job(NotificationJobType type, Long targetId, String detail) {
        NotificationJob job = new NotificationJob(type, targetId, detail, NOW);
        ReflectionTestUtils.setField(job, "id", JOB_ID);
        given(notificationJobRepository.findById(JOB_ID)).willReturn(Optional.of(job));
        return job;
    }

    // 작업 하나를 처리한다
    private NotificationJob process(NotificationJobType type, Long targetId, String detail) {
        NotificationJob job = job(type, targetId, detail);
        dispatcher.process(JOB_ID, NOW);
        return job;
    }

    private void givenApplication() {
        given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.of(application));
    }

    private void verifyToStudent(NotificationType type, String message) {
        verify(notificationService).notify(STUDENT_ID, type, message, NotificationTargetType.HELP_REQUEST, REQUEST_ID);
    }

    private void verifyToHelper(NotificationType type, String message) {
        verify(notificationService).notify(HELPER_ID, type, message, NotificationTargetType.APPLICATION, APPLICATION_ID);
    }

    @Test
    void process_바로매칭_장애학생과도우미모두매칭완료하고작업완료() {
        // given
        givenApplication();

        // when
        NotificationJob job = process(NotificationJobType.HELPER_CONFIRMED, APPLICATION_ID,
                HelperConfirmedEvent.Kind.DIRECT_MATCH.name());

        // then
        verifyToStudent(NotificationType.REQUEST_MATCHED, "10월 12일(월) 12:00 식사 도우미가 매칭됐어요.");
        verifyToHelper(NotificationType.APPLICATION_MATCHED, "10월 12일(월) 12:00 식사 도우미로 매칭됐어요.");
        assertThat(job.getStatus()).isEqualTo(NotificationJobStatus.DONE);
        assertThat(job.getProcessedAt()).isEqualTo(NOW);
    }

    @Test
    void process_승격_장애학생도우미바뀜과도우미승격() {
        // given
        givenApplication();

        // when
        process(NotificationJobType.HELPER_CONFIRMED, APPLICATION_ID, HelperConfirmedEvent.Kind.PROMOTED.name());

        // then
        verifyToStudent(NotificationType.HELPER_CHANGED, "10월 12일(월) 12:00 식사 도우미가 바뀌었어요.");
        verifyToHelper(NotificationType.PROMOTED, "10월 12일(월) 12:00 신청에 예비에서 매칭으로 승격됐어요.");
    }

    @Test
    void process_승격수락_장애학생에게만도우미바뀜() {
        // given
        givenApplication();

        // when
        process(NotificationJobType.HELPER_CONFIRMED, APPLICATION_ID,
                HelperConfirmedEvent.Kind.PROMOTION_ACCEPTED.name());

        // then
        verifyToStudent(NotificationType.HELPER_CHANGED, "10월 12일(월) 12:00 식사 도우미가 바뀌었어요.");
        verify(notificationService, never()).notify(eq(HELPER_ID), eq(NotificationType.PROMOTED), anyString(),
                eq(NotificationTargetType.APPLICATION), anyLong());
    }

    @Test
    void process_승격응답재알림_도우미에게마감포함하고메일문자에도마감과재알림여부() {
        // given
        application.promote(NOON.minusMinutes(40), NOON.minusMinutes(15), NOON.minusMinutes(30), 1);
        givenApplication();

        // when
        process(NotificationJobType.PROMOTION_PENDING, APPLICATION_ID, "true");

        // then
        verifyToHelper(NotificationType.PROMOTION_RESPONSE_REQUIRED,
                "다시 알려 드려요. 10월 12일(월) 12:00 신청에 예비에서 승격됐어요. 11:45까지 갈 수 있는지 알려 주세요.");
        verify(outboxSender).send(eq(NotificationType.PROMOTION_RESPONSE_REQUIRED),
                eq(new Recipient(helper.getEmail(), helper.getPhone())), anyString(), eq(NOON),
                eq(NOON.minusMinutes(15)), eq(true), eq(APPLICATION_ID));
    }

    @Test
    void process_예비등록과자동제외_도우미에게() {
        // given
        givenApplication();

        // when
        process(NotificationJobType.WAITING_REGISTERED, APPLICATION_ID, "2");
        process(NotificationJobType.WAITING_EXCLUDED, APPLICATION_ID, null);

        // then
        verifyToHelper(NotificationType.WAITING_REGISTERED, "10월 12일(월) 12:00 신청에 예비 2번으로 등록됐어요.");
        verifyToHelper(NotificationType.WAITING_EXCLUDED, "10월 12일(월) 12:00 예비 자리가 같은 시간 다른 매칭으로 빠졌어요.");
    }

    @Test
    void process_다시모집중과매칭실패_장애학생에게() {
        // given
        given(helpRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(request));

        // when
        process(NotificationJobType.REQUEST_REOPENED, REQUEST_ID, null);
        process(NotificationJobType.REQUEST_FAILED, REQUEST_ID, null);

        // then
        verifyToStudent(NotificationType.REQUEST_REOPENED, "10월 12일(월) 12:00 신청의 도우미가 빠져 다시 모집 중이에요.");
        verifyToStudent(NotificationType.REQUEST_FAILED, "10월 12일(월) 12:00 신청에 도우미가 매칭되지 않았어요.");
    }

    @Test
    void process_학생취소_그지원의도우미에게() {
        // given
        givenApplication();

        // when
        process(NotificationJobType.STUDENT_CANCELED, APPLICATION_ID, null);

        // then
        verifyToHelper(NotificationType.STUDENT_CANCELED, "10월 12일(월) 12:00 신청이 장애학생 사정으로 취소됐어요.");
    }

    @Test
    void process_메일문자_받는사람연락처와이동대상으로Sender에넘김() {
        // given
        givenApplication();

        // when
        process(NotificationJobType.HELPER_CONFIRMED, APPLICATION_ID, HelperConfirmedEvent.Kind.DIRECT_MATCH.name());

        // then — 장애학생은 학교 이메일·등록 전화번호, 도우미는 가입 이메일·전화번호. 보낼지는 Sender가 종류로 정한다
        verify(outboxSender).send(NotificationType.REQUEST_MATCHED,
                new Recipient(student.getSchoolEmail(), student.getPhone()),
                "10월 12일(월) 12:00 식사 도우미가 매칭됐어요.", NOON, null, false, REQUEST_ID);
        verify(outboxSender).send(NotificationType.APPLICATION_MATCHED,
                new Recipient(helper.getEmail(), helper.getPhone()),
                "10월 12일(월) 12:00 식사 도우미로 매칭됐어요.", NOON, null, false, APPLICATION_ID);
    }

    @Test
    void process_대상지원이사라짐_알림없이작업완료() {
        // given
        given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.empty());

        // when
        NotificationJob job = process(NotificationJobType.WAITING_EXCLUDED, APPLICATION_ID, null);

        // then
        verifyNoInteractions(notificationService, outboxSender);
        assertThat(job.getStatus()).isEqualTo(NotificationJobStatus.DONE);
    }

    @Test
    void markFailed_재시도가남음_1분뒤다시예약() {
        // given
        NotificationJob job = job(NotificationJobType.REQUEST_FAILED, REQUEST_ID, null);

        // when
        dispatcher.markFailed(JOB_ID, "db down", NOW);

        // then
        assertThat(job.getStatus()).isEqualTo(NotificationJobStatus.PENDING);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(job.getLastError()).isEqualTo("db down");
    }

    @Test
    void markFailed_세번재시도후_FAILED로남김() {
        // given
        NotificationJob job = job(NotificationJobType.REQUEST_FAILED, REQUEST_ID, null);

        // when — 처음 + 재시도 3번 모두 실패
        for (int i = 0; i < 4; i++) {
            dispatcher.markFailed(JOB_ID, "db down", NOW);
        }

        // then
        assertThat(job.getStatus()).isEqualTo(NotificationJobStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(4);
    }
}
