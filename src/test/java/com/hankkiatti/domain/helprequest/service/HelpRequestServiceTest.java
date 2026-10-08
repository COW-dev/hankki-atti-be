package com.hankkiatti.domain.helprequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.dto.request.HelpRequestCreateRequestDto;
import com.hankkiatti.domain.helprequest.dto.response.HelpRequestCreateResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.MyHelpRequestResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.MyHelpRequestsResponseDto;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.entity.RequestCancelType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestErrorType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestException;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestAccounts;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HelpRequestServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-10-12(월) 09:00
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDateTime NOON = MONDAY.atTime(12, 0);
    private static final Long STUDENT_ID = 1L;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    private HelpRequestService helpRequestService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(MONDAY.atTime(9, 0).atZone(SEOUL).toInstant(), SEOUL);
        helpRequestService = new HelpRequestService(helpRequestRepository, studentRepository, applicationRepository,
                new HelpRequestSchedule(), clock);
    }

    private void givenStudentWithoutOverlap(LocalDateTime startAt) {
        Student student = TestProfiles.student(TestAccounts.withId(STUDENT_ID, AccountRole.STUDENT, "hash", false));
        given(studentRepository.findByIdForUpdate(STUDENT_ID)).willReturn(Optional.of(student));
        given(helpRequestRepository.existsOverlapping(STUDENT_ID, startAt, startAt.plusHours(1))).willReturn(false);
        given(helpRequestRepository.save(any(HelpRequest.class))).willAnswer(invocation -> {
            HelpRequest saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });
    }

    private HelpRequestCreateRequestDto request(LocalDateTime startAt, Set<HelpType> helpTypes, String otherHelpText,
                                                String memo) {
        return new HelpRequestCreateRequestDto(startAt, helpTypes, otherHelpText, memo);
    }

    private void assertErrorType(Runnable call, HelpRequestErrorType expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(HelpRequestException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    @Test
    void create_정상_모집중으로저장하고요약반환() {
        // given
        givenStudentWithoutOverlap(NOON);

        // when
        HelpRequestCreateResponseDto result = helpRequestService.create(STUDENT_ID,
                request(NOON, Set.of(HelpType.OTHER, HelpType.SERVING), " 식판 반납 ", " 출입구에서 기다릴게요 "));

        // then
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.startAt()).isEqualTo(NOON);
        assertThat(result.endAt()).isEqualTo(NOON.plusHours(1));
        assertThat(result.helpTypes()).containsExactly(HelpType.SERVING, HelpType.OTHER);
        assertThat(result.otherHelpText()).isEqualTo("식판 반납");
        assertThat(result.memo()).isEqualTo("출입구에서 기다릴게요");
        assertThat(result.status()).isEqualTo(HelpRequestStatus.RECRUITING);
    }

    @Test
    void create_기타를안고르면기타내용버리고_빈메모는없음으로저장() {
        // given
        givenStudentWithoutOverlap(NOON);

        // when
        helpRequestService.create(STUDENT_ID, request(NOON, Set.of(HelpType.SEATING), "무시될 내용", "   "));

        // then
        ArgumentCaptor<HelpRequest> saved = ArgumentCaptor.forClass(HelpRequest.class);
        verify(helpRequestRepository).save(saved.capture());
        assertThat(saved.getValue().getOtherHelpText()).isNull();
        assertThat(saved.getValue().getMemo()).isNull();
    }

    @Test
    void create_선택지에없는시각_START_TIME_NOT_AVAILABLE이고DB조회없음() {
        // when & then — 토요일, 30분 단위 아님, 이미 지난 시각
        assertErrorType(() -> helpRequestService.create(STUDENT_ID,
                request(MONDAY.plusDays(5).atTime(12, 0), Set.of(HelpType.SERVING), null, null)),
                HelpRequestErrorType.START_TIME_NOT_AVAILABLE);
        assertErrorType(() -> helpRequestService.create(STUDENT_ID,
                request(MONDAY.atTime(12, 15), Set.of(HelpType.SERVING), null, null)),
                HelpRequestErrorType.START_TIME_NOT_AVAILABLE);
        assertErrorType(() -> helpRequestService.create(STUDENT_ID,
                request(MONDAY.minusDays(1).atTime(12, 0), Set.of(HelpType.SERVING), null, null)),
                HelpRequestErrorType.START_TIME_NOT_AVAILABLE);
        verify(studentRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void create_내신청과시간이겹침_TIME_OVERLAP이고저장안함() {
        // given
        Student student = TestProfiles.student(TestAccounts.withId(STUDENT_ID, AccountRole.STUDENT, "hash", false));
        given(studentRepository.findByIdForUpdate(STUDENT_ID)).willReturn(Optional.of(student));
        given(helpRequestRepository.existsOverlapping(STUDENT_ID, NOON, NOON.plusHours(1))).willReturn(true);

        // when & then
        assertErrorType(() -> helpRequestService.create(STUDENT_ID, request(NOON, Set.of(HelpType.SERVING), null, null)),
                HelpRequestErrorType.TIME_OVERLAP);
        verify(helpRequestRepository, never()).save(any());
    }

    @Test
    void create_장애학생이아닌계정_ACCESS_DENIED() {
        // given
        given(studentRepository.findByIdForUpdate(2L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> helpRequestService.create(2L, request(NOON, Set.of(HelpType.SERVING), null, null)))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
        verify(helpRequestRepository, never()).save(any());
    }

    // ---- 내 신청 조회 (지금 = 2026-10-12(월) 09:00) ----

    private Student myStudent() {
        return TestProfiles.student(TestAccounts.withId(STUDENT_ID, AccountRole.STUDENT, "hash", false));
    }

    private HelpRequest requestWithId(Long id, Student student, LocalDateTime startAt) {
        HelpRequest request = new HelpRequest(student, startAt, Set.of(HelpType.SEATING, HelpType.SERVING), null, null);
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }

    private Application matchedApplication(Long id, HelpRequest request, Helper helper) {
        Application application = new Application(request, helper, request.getStartAt().minusDays(1));
        application.match(request.getStartAt().minusDays(1));
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }

    @Test
    void getMyRequests_진행중은다가오는가까운순_끝난건지난최근순_매칭건만도우미() {
        // given
        Student student = myStudent();
        Helper helper = TestProfiles.helper(TestAccounts.withId(9L, AccountRole.HELPER, "hash", false), "60230001");
        HelpRequest laterRecruiting = requestWithId(1L, student, NOON.plusDays(2));
        HelpRequest soonMatched = requestWithId(2L, student, NOON);
        soonMatched.match(NOON.minusDays(1));
        HelpRequest withdrawnFuture = requestWithId(3L, student, NOON.plusDays(1));
        withdrawnFuture.withdraw(NOON.minusDays(1));
        HelpRequest failedPast = requestWithId(4L, student, NOON.minusDays(3));
        failedPast.fail();
        HelpRequest completedPast = requestWithId(5L, student, NOON.minusDays(1));
        completedPast.match(NOON.minusDays(2));
        completedPast.complete(NOON.minusDays(1).plusHours(1));
        given(studentRepository.existsById(STUDENT_ID)).willReturn(true);
        given(helpRequestRepository.findByStudentAccountId(STUDENT_ID))
                .willReturn(List.of(laterRecruiting, soonMatched, withdrawnFuture, failedPast, completedPast));
        given(applicationRepository.findMatchedWithHelper(List.of(1L, 2L, 3L, 4L, 5L)))
                .willReturn(List.of(matchedApplication(20L, soonMatched, helper)));

        // when
        MyHelpRequestsResponseDto result = helpRequestService.getMyRequests(STUDENT_ID);

        // then
        assertThat(result.upcoming()).extracting(MyHelpRequestResponseDto::id).containsExactly(2L, 1L);
        assertThat(result.past()).extracting(MyHelpRequestResponseDto::id).containsExactly(3L, 5L, 4L);
        MyHelpRequestResponseDto matched = result.upcoming().get(0);
        assertThat(matched.helper().name()).isEqualTo("이도움");
        assertThat(matched.helper().kakaoId()).isEqualTo("kakao_helper");
        assertThat(matched.helpTypes()).containsExactly(HelpType.SERVING, HelpType.SEATING);
        assertThat(result.upcoming().get(1).helper()).isNull();
    }

    @Test
    void getMyRequests_이용완료24시간이내만_노쇼신고가능과마감시각() {
        // given — 어제 13:00 이용 완료(마감 오늘 13:00), 그제 13:00 이용 완료(마감 지남)
        Student student = myStudent();
        HelpRequest yesterday = requestWithId(1L, student, NOON.minusDays(1));
        yesterday.match(NOON.minusDays(2));
        yesterday.complete(NOON.minusDays(1).plusHours(1));
        HelpRequest twoDaysAgo = requestWithId(2L, student, NOON.minusDays(2));
        twoDaysAgo.match(NOON.minusDays(3));
        twoDaysAgo.complete(NOON.minusDays(2).plusHours(1));
        given(studentRepository.existsById(STUDENT_ID)).willReturn(true);
        given(helpRequestRepository.findByStudentAccountId(STUDENT_ID)).willReturn(List.of(yesterday, twoDaysAgo));
        given(applicationRepository.findMatchedWithHelper(List.of(1L, 2L))).willReturn(List.of());

        // when
        MyHelpRequestsResponseDto result = helpRequestService.getMyRequests(STUDENT_ID);

        // then
        assertThat(result.past().get(0).noShowReportable()).isTrue();
        assertThat(result.past().get(0).noShowDeadline()).isEqualTo(NOON.plusHours(1));
        assertThat(result.past().get(1).noShowReportable()).isFalse();
        assertThat(result.past().get(1).noShowDeadline()).isNull();
    }

    @Test
    void getMyRequests_신청없음_빈두목록이고지원조회안함() {
        // given
        given(studentRepository.existsById(STUDENT_ID)).willReturn(true);
        given(helpRequestRepository.findByStudentAccountId(STUDENT_ID)).willReturn(List.of());

        // when
        MyHelpRequestsResponseDto result = helpRequestService.getMyRequests(STUDENT_ID);

        // then
        assertThat(result.upcoming()).isEmpty();
        assertThat(result.past()).isEmpty();
        verify(applicationRepository, never()).findMatchedWithHelper(any());
    }

    @Test
    void getMyRequests_장애학생이아닌계정_ACCESS_DENIED() {
        // given
        given(studentRepository.existsById(2L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> helpRequestService.getMyRequests(2L))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
    }

    // ---- 신청 철회 (지금 = 2026-10-12(월) 09:00) ----

    private HelpRequest myRecruitingRequest(Long id, LocalDateTime startAt) {
        Student student = myStudent();
        ReflectionTestUtils.setField(student, "accountId", STUDENT_ID);
        return requestWithId(id, student, startAt);
    }

    @Test
    void withdraw_내모집중신청_취소완료로바뀐신청반환() {
        // given
        HelpRequest request = myRecruitingRequest(1L, NOON);
        given(studentRepository.existsById(STUDENT_ID)).willReturn(true);
        given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(request));

        // when
        MyHelpRequestResponseDto result = helpRequestService.withdraw(STUDENT_ID, 1L);

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.CANCELED);
        assertThat(request.getCancelType()).isEqualTo(RequestCancelType.STUDENT_WITHDRAW);
        assertThat(request.getCanceledAt()).isEqualTo(MONDAY.atTime(9, 0));
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(HelpRequestStatus.CANCELED);
        assertThat(result.helper()).isNull();
    }

    @Test
    void withdraw_없는신청과남의신청_NOT_FOUND() {
        // given
        HelpRequest othersRequest = myRecruitingRequest(2L, NOON);
        given(studentRepository.existsById(3L)).willReturn(true);
        given(helpRequestRepository.findByIdForUpdate(2L)).willReturn(Optional.of(othersRequest));
        given(helpRequestRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

        // when & then
        assertErrorType(() -> helpRequestService.withdraw(3L, 2L), HelpRequestErrorType.NOT_FOUND);
        assertErrorType(() -> helpRequestService.withdraw(3L, 99L), HelpRequestErrorType.NOT_FOUND);
        assertThat(othersRequest.getStatus()).isEqualTo(HelpRequestStatus.RECRUITING);
    }

    @Test
    void withdraw_매칭완료신청_INVALID_STATUS() {
        // given
        HelpRequest request = myRecruitingRequest(1L, NOON);
        request.match(MONDAY.atTime(8, 0));
        given(studentRepository.existsById(STUDENT_ID)).willReturn(true);
        given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(request));

        // when & then
        assertErrorType(() -> helpRequestService.withdraw(STUDENT_ID, 1L), HelpRequestErrorType.INVALID_STATUS);
    }

    @Test
    void withdraw_장애학생이아닌계정_ACCESS_DENIED이고신청을잠그지않음() {
        // given
        given(studentRepository.existsById(2L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> helpRequestService.withdraw(2L, 1L))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
        verify(helpRequestRepository, never()).findByIdForUpdate(anyLong());
    }

    // ---- 노쇼 신고 (지금 = 2026-10-12(월) 09:00) ----

    private HelpRequest myCompletedRequest(LocalDateTime completedAt) {
        HelpRequest request = myRecruitingRequest(1L, completedAt.minusHours(1));
        request.match(completedAt.minusDays(1));
        request.complete(completedAt);
        return request;
    }

    private Application completedApplication(HelpRequest request) {
        Helper helper = TestProfiles.helper(TestAccounts.withId(9L, AccountRole.HELPER, "hash", false), "60230001");
        Application application = matchedApplication(20L, request, helper);
        application.complete();
        return application;
    }

    @Test
    void reportNoShow_24시간이내_신청과지원모두노쇼이고봉사시간0() {
        // given — 어제 13:00 이용 완료, 마감 오늘 13:00
        HelpRequest request = myCompletedRequest(MONDAY.minusDays(1).atTime(13, 0));
        Application application = completedApplication(request);
        given(studentRepository.existsById(STUDENT_ID)).willReturn(true);
        given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(request));
        given(applicationRepository.findByHelpRequestIdAndStatus(1L, ApplicationStatus.COMPLETED))
                .willReturn(List.of(application));

        // when
        MyHelpRequestResponseDto result = helpRequestService.reportNoShow(STUDENT_ID, 1L);

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.NO_SHOW);
        assertThat(request.getNoShowReportedAt()).isEqualTo(MONDAY.atTime(9, 0));
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.NO_SHOW);
        assertThat(application.getVolunteerHours()).isEqualByComparingTo("0");
        assertThat(result.status()).isEqualTo(HelpRequestStatus.NO_SHOW);
        assertThat(result.helper().name()).isEqualTo("이도움");
        assertThat(result.noShowReportable()).isFalse();
    }

    @Test
    void reportNoShow_24시간지남_NO_SHOW_PERIOD_EXPIRED이고지원은그대로() {
        // given — 그제 13:00 이용 완료, 마감 어제 13:00
        HelpRequest request = myCompletedRequest(MONDAY.minusDays(2).atTime(13, 0));
        given(studentRepository.existsById(STUDENT_ID)).willReturn(true);
        given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(request));

        // when & then
        assertErrorType(() -> helpRequestService.reportNoShow(STUDENT_ID, 1L),
                HelpRequestErrorType.NO_SHOW_PERIOD_EXPIRED);
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.COMPLETED);
        verify(applicationRepository, never()).findByHelpRequestIdAndStatus(anyLong(), any());
    }

    @Test
    void reportNoShow_이용완료가아님_INVALID_STATUS() {
        // given
        HelpRequest request = myRecruitingRequest(1L, NOON);
        request.match(MONDAY.atTime(8, 0));
        given(studentRepository.existsById(STUDENT_ID)).willReturn(true);
        given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(request));

        // when & then
        assertErrorType(() -> helpRequestService.reportNoShow(STUDENT_ID, 1L), HelpRequestErrorType.INVALID_STATUS);
    }

    @Test
    void reportNoShow_남의신청_NOT_FOUND() {
        // given
        HelpRequest othersRequest = myCompletedRequest(MONDAY.minusDays(1).atTime(13, 0));
        given(studentRepository.existsById(3L)).willReturn(true);
        given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(othersRequest));

        // when & then
        assertErrorType(() -> helpRequestService.reportNoShow(3L, 1L), HelpRequestErrorType.NOT_FOUND);
        assertThat(othersRequest.getStatus()).isEqualTo(HelpRequestStatus.COMPLETED);
    }
}
