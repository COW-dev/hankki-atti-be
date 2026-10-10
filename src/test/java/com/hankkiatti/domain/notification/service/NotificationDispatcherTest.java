package com.hankkiatti.domain.notification.service;

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
import com.hankkiatti.domain.application.event.PromotionPendingEvent;
import com.hankkiatti.domain.application.event.WaitingExcludedEvent;
import com.hankkiatti.domain.application.event.WaitingRegisteredEvent;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.event.HelpRequestCanceledByStudentEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestFailedEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestReopenedEvent;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.notification.entity.NotificationTargetType;
import com.hankkiatti.domain.notification.entity.NotificationType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.support.TestAccounts;
import com.hankkiatti.support.TestHelpRequests;
import com.hankkiatti.support.TestProfiles;
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
    private static final Long STUDENT_ID = 1L;
    private static final Long HELPER_ID = 7L;
    private static final Long REQUEST_ID = 10L;
    private static final Long APPLICATION_ID = 31L;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private NotificationService notificationService;

    private NotificationDispatcher dispatcher;

    private HelpRequest request;
    private Application application;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(applicationRepository, helpRequestRepository, notificationService);
        Student student = TestHelpRequests.student("60231234");
        ReflectionTestUtils.setField(student, "accountId", STUDENT_ID);
        request = TestHelpRequests.request(student, NOON);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        Helper helper = TestProfiles.helper(TestAccounts.withId(HELPER_ID, AccountRole.HELPER, "hash", false), "60230001");
        ReflectionTestUtils.setField(helper, "accountId", HELPER_ID);
        application = new Application(request, helper, NOON.minusDays(1));
        ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
    }

    private void givenApplication() {
        given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.of(application));
    }

    private HelperConfirmedEvent confirmed(HelperConfirmedEvent.Kind kind) {
        return new HelperConfirmedEvent(HELPER_ID, REQUEST_ID, APPLICATION_ID, NOON, NOON.plusHours(1), kind);
    }

    private void verifyToStudent(NotificationType type, String message) {
        verify(notificationService).notify(STUDENT_ID, type, message, NotificationTargetType.HELP_REQUEST, REQUEST_ID);
    }

    private void verifyToHelper(NotificationType type, String message) {
        verify(notificationService).notify(HELPER_ID, type, message, NotificationTargetType.APPLICATION, APPLICATION_ID);
    }

    @Test
    void helperConfirmed_바로매칭_장애학생과도우미모두매칭완료() {
        // given
        givenApplication();

        // when
        dispatcher.helperConfirmed(confirmed(HelperConfirmedEvent.Kind.DIRECT_MATCH));

        // then
        verifyToStudent(NotificationType.REQUEST_MATCHED, "10월 12일(월) 12:00 식사 도우미가 매칭됐어요.");
        verifyToHelper(NotificationType.APPLICATION_MATCHED, "10월 12일(월) 12:00 식사 도우미로 매칭됐어요.");
    }

    @Test
    void helperConfirmed_승격_장애학생도우미바뀜과도우미승격() {
        // given
        givenApplication();

        // when
        dispatcher.helperConfirmed(confirmed(HelperConfirmedEvent.Kind.PROMOTED));

        // then
        verifyToStudent(NotificationType.HELPER_CHANGED, "10월 12일(월) 12:00 식사 도우미가 바뀌었어요.");
        verifyToHelper(NotificationType.PROMOTED, "10월 12일(월) 12:00 신청에 예비에서 매칭으로 승격됐어요.");
    }

    @Test
    void helperConfirmed_승격수락_장애학생에게만도우미바뀜() {
        // given
        givenApplication();

        // when
        dispatcher.helperConfirmed(confirmed(HelperConfirmedEvent.Kind.PROMOTION_ACCEPTED));

        // then
        verifyToStudent(NotificationType.HELPER_CHANGED, "10월 12일(월) 12:00 식사 도우미가 바뀌었어요.");
        verify(notificationService, never()).notify(eq(HELPER_ID), eq(NotificationType.PROMOTED), anyString(),
                eq(NotificationTargetType.APPLICATION), anyLong());
    }

    @Test
    void promotionPending_도우미에게응답마감포함() {
        // given
        application.promote(NOON.minusMinutes(40), NOON.minusMinutes(15), NOON.minusMinutes(30));
        givenApplication();

        // when
        dispatcher.promotionPending(new PromotionPendingEvent(APPLICATION_ID, true));

        // then
        verifyToHelper(NotificationType.PROMOTION_RESPONSE_REQUIRED,
                "다시 알려 드려요. 10월 12일(월) 12:00 신청에 예비에서 승격됐어요. 11:45까지 갈 수 있는지 알려 주세요.");
    }

    @Test
    void waitingRegistered와waitingExcluded_도우미에게() {
        // given
        givenApplication();

        // when
        dispatcher.waitingRegistered(new WaitingRegisteredEvent(APPLICATION_ID, 2));
        dispatcher.waitingExcluded(new WaitingExcludedEvent(APPLICATION_ID));

        // then
        verifyToHelper(NotificationType.WAITING_REGISTERED, "10월 12일(월) 12:00 신청에 예비 2번으로 등록됐어요.");
        verifyToHelper(NotificationType.WAITING_EXCLUDED, "10월 12일(월) 12:00 예비 자리가 같은 시간 다른 매칭으로 빠졌어요.");
    }

    @Test
    void requestReopened와requestFailed_장애학생에게() {
        // given
        given(helpRequestRepository.findById(REQUEST_ID)).willReturn(Optional.of(request));

        // when
        dispatcher.requestReopened(new HelpRequestReopenedEvent(REQUEST_ID));
        dispatcher.requestFailed(new HelpRequestFailedEvent(REQUEST_ID, STUDENT_ID, NOON));

        // then
        verifyToStudent(NotificationType.REQUEST_REOPENED, "10월 12일(월) 12:00 신청의 도우미가 빠져 다시 모집 중이에요.");
        verifyToStudent(NotificationType.REQUEST_FAILED, "10월 12일(월) 12:00 신청에 도우미가 매칭되지 않았어요.");
    }

    @Test
    void canceledByStudent_지원마다그도우미에게() {
        // given
        givenApplication();
        given(applicationRepository.findById(99L)).willReturn(Optional.empty());

        // when — 99번은 그사이 사라졌다
        dispatcher.canceledByStudent(new HelpRequestCanceledByStudentEvent(REQUEST_ID, NOON, List.of(APPLICATION_ID, 99L)));

        // then
        verifyToHelper(NotificationType.STUDENT_CANCELED, "10월 12일(월) 12:00 신청이 장애학생 사정으로 취소됐어요.");
    }

    @Test
    void 대상지원이없음_알림없음() {
        // given
        given(applicationRepository.findById(APPLICATION_ID)).willReturn(Optional.empty());

        // when
        dispatcher.waitingExcluded(new WaitingExcludedEvent(APPLICATION_ID));

        // then
        verifyNoInteractions(notificationService);
    }
}
