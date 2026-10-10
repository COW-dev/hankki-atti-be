package com.hankkiatti.domain.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.entity.AdminGrade;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.dto.response.AdminStudentHeaderResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentHelpRequestResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentHelpRequestsResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentHistoriesResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentHistoryResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentHistoryType;
import com.hankkiatti.domain.student.dto.response.AdminStudentInfoResponseDto;
import com.hankkiatti.domain.student.dto.response.LimitedAdminStudentHeaderResponseDto;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.exception.StudentErrorType;
import com.hankkiatti.domain.student.exception.StudentException;
import com.hankkiatti.domain.student.repository.StudentRepository;
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
class AdminStudentDetailServiceTest {

    // 2026-10-12(월) 12:00
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 12, 12, 0);
    private static final Long ADMIN_ID = 100L;
    private static final Long STUDENT_ID = 1L;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    private AdminStudentDetailService service;

    private Student student;
    private long nextId = 10;

    @BeforeEach
    void setUp() {
        service = new AdminStudentDetailService(adminRepository, studentRepository, helpRequestRepository,
                applicationRepository);
        student = new Student(TestAccounts.withId(STUDENT_ID, AccountRole.STUDENT, "hash", false), "김민지",
                "60231234", "010-1234-1234", "minji_k", "60231234@mju.ac.kr", DisabilityType.VISUAL, "메뉴 읽어 주기");
        ReflectionTestUtils.setField(student, "accountId", STUDENT_ID);
        ReflectionTestUtils.setField(student, "createdAt", NOON.minusDays(20));
    }

    private void givenAdmin(AdminGrade grade) {
        Admin admin = new Admin(TestAccounts.withId(ADMIN_ID, AccountRole.ADMIN, "hash", false), "김센터", grade);
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
    }

    private void givenStudent() {
        given(studentRepository.findById(STUDENT_ID)).willReturn(Optional.of(student));
    }

    private HelpRequest request(LocalDateTime startAt) {
        HelpRequest request = TestHelpRequests.request(student, startAt);
        ReflectionTestUtils.setField(request, "id", nextId++);
        ReflectionTestUtils.setField(request, "createdAt", startAt.minusDays(2));
        return request;
    }

    private Helper helper(Long id) {
        Helper helper = TestProfiles.helper(TestAccounts.withId(id, AccountRole.HELPER, "hash", false), "6023000" + id);
        ReflectionTestUtils.setField(helper, "accountId", id);
        return helper;
    }

    private Application application(HelpRequest request, Helper helper) {
        Application application = new Application(request, helper, request.getStartAt().minusDays(1));
        ReflectionTestUtils.setField(application, "id", nextId++);
        return application;
    }

    private void givenRequests(List<HelpRequest> requests, List<Application> applications) {
        given(helpRequestRepository.findByStudentAccountId(STUDENT_ID)).willReturn(requests);
        given(applicationRepository.findWithHelperByHelpRequestIdIn(any())).willReturn(applications);
    }

    @Test
    void getInfo_전체권한_연락처장애유형특이사항까지() {
        // given
        givenAdmin(AdminGrade.FULL);
        givenStudent();

        // when
        AdminStudentInfoResponseDto info = service.getInfo(ADMIN_ID, STUDENT_ID);

        // then
        assertThat(info.name()).isEqualTo("김민지");
        assertThat(info.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(info.disabilityType()).isEqualTo(DisabilityType.VISUAL);
        assertThat(info.phone()).isEqualTo("010-1234-1234");
        assertThat(info.kakaoId()).isEqualTo("minji_k");
        assertThat(info.specialNote()).isEqualTo("메뉴 읽어 주기");
        assertThat(info.registeredAt()).isEqualTo(NOON.minusDays(20));
    }

    @Test
    void getInfo_제한권한_ACCESS_DENIED이고학생을읽지않음() {
        // given
        givenAdmin(AdminGrade.LIMITED);

        // when & then
        assertThatThrownBy(() -> service.getInfo(ADMIN_ID, STUDENT_ID))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
        verify(studentRepository, never()).findById(any());
    }

    @Test
    void getHelpRequests_없는학생_NOT_FOUND() {
        // given
        givenAdmin(AdminGrade.LIMITED);
        given(studentRepository.findById(STUDENT_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getHelpRequests(ADMIN_ID, STUDENT_ID))
                .isInstanceOf(StudentException.class)
                .extracting("errorCode").isEqualTo(StudentErrorType.NOT_FOUND);
    }

    @Test
    void getHelpRequests_최근식사부터_확정도우미와승격순번_등급별프로필() {
        // given — 이전 신청은 바로 매칭, 최근 신청은 도우미가 빠져 예비 1번이 승격
        givenAdmin(AdminGrade.LIMITED);
        givenStudent();
        HelpRequest older = request(NOON.minusDays(3));
        older.match(NOON.minusDays(4));
        Application direct = application(older, helper(7L));
        direct.match(NOON.minusDays(4));
        HelpRequest latest = request(NOON);
        latest.match(NOON.minusDays(1));
        latest.changeHelper();
        Application promoted = application(latest, helper(8L));
        promoted.promote(NOON.minusHours(5), null, null, 1);
        givenRequests(List.of(older, latest), List.of(direct, promoted));

        // when
        AdminStudentHelpRequestsResponseDto result = service.getHelpRequests(ADMIN_ID, STUDENT_ID);

        // then
        assertThat(result.student()).isInstanceOf(LimitedAdminStudentHeaderResponseDto.class);
        List<AdminStudentHelpRequestResponseDto> rows = result.helpRequests();
        assertThat(rows).extracting(AdminStudentHelpRequestResponseDto::id)
                .containsExactly(latest.getId(), older.getId());
        assertThat(rows.get(0).helper().id()).isEqualTo(8L);
        assertThat(rows.get(0).promoted()).isTrue();
        assertThat(rows.get(0).promotedWaitingOrder()).isEqualTo(1);
        assertThat(rows.get(0).helperChanged()).isTrue();
        assertThat(rows.get(1).helper().id()).isEqualTo(7L);
        assertThat(rows.get(1).promoted()).isFalse();
        assertThat(rows.get(1).promotedWaitingOrder()).isNull();
    }

    @Test
    void getHelpRequests_신청없음_지원을읽지않음_전체권한프로필() {
        // given
        givenAdmin(AdminGrade.FULL);
        givenStudent();
        given(helpRequestRepository.findByStudentAccountId(STUDENT_ID)).willReturn(List.of());

        // when
        AdminStudentHelpRequestsResponseDto result = service.getHelpRequests(ADMIN_ID, STUDENT_ID);

        // then
        assertThat(result.helpRequests()).isEmpty();
        assertThat(((AdminStudentHeaderResponseDto) result.student()).disabilityType())
                .isEqualTo(DisabilityType.VISUAL);
        verify(applicationRepository, never()).findWithHelperByHelpRequestIdIn(any());
    }

    @Test
    void getHistories_도우미취소승격_모집재개_학생취소_노쇼_철회는빠짐() {
        // given
        givenAdmin(AdminGrade.FULL);
        givenStudent();
        LocalDateTime canceledAt = NOON.minusHours(26);
        // ① 학사 일정으로 도우미 7 취소 → 예비 1번 도우미 8 승격
        HelpRequest promotedRequest = request(NOON);
        promotedRequest.match(NOON.minusDays(2));
        Application canceled = application(promotedRequest, helper(7L));
        canceled.match(NOON.minusDays(2));
        canceled.cancelByHelper(CancelReason.ACADEMIC, null, canceledAt);
        canceled.recordAfterAction(ApplicationAfterAction.PROMOTED);
        Application promoted = application(promotedRequest, helper(8L));
        promoted.promote(canceledAt, null, null, 1);
        // ② 기타 사유로 도우미 9 취소(식사 40분 전) → 예비 없어 모집 재개 → 매칭 실패
        HelpRequest reopened = request(NOON.minusDays(5));
        reopened.match(NOON.minusDays(6));
        Application otherCanceled = application(reopened, helper(9L));
        otherCanceled.match(NOON.minusDays(6));
        otherCanceled.cancelByHelper(CancelReason.OTHER, "알바 시간이 겹쳤어요", NOON.minusDays(5).minusMinutes(40));
        otherCanceled.recordAfterAction(ApplicationAfterAction.REOPENED);
        reopened.reopen();
        reopened.fail();
        // ③ 장애학생 매칭 취소 (식사 2시간 전)
        HelpRequest studentCanceled = request(NOON.minusDays(7));
        studentCanceled.match(NOON.minusDays(8));
        studentCanceled.cancelByStudent(NOON.minusDays(7).minusHours(2));
        // ④ 노쇼 (이용 완료 3시간 뒤 신고)
        HelpRequest noShow = request(NOON.minusDays(10));
        noShow.match(NOON.minusDays(11));
        Application noShowApplication = application(noShow, helper(10L));
        noShowApplication.match(NOON.minusDays(11));
        noShowApplication.complete();
        noShowApplication.markNoShow();
        noShow.complete(NOON.minusDays(10).plusHours(1));
        noShow.reportNoShow(NOON.minusDays(10).plusHours(4));
        // ⑤ 모집 중 철회 — 이력에 넣지 않는다
        HelpRequest withdrawn = request(NOON.minusDays(12));
        withdrawn.withdraw(NOON.minusDays(13));
        givenRequests(List.of(withdrawn, noShow, studentCanceled, reopened, promotedRequest),
                List.of(canceled, promoted, otherCanceled, noShowApplication));

        // when
        AdminStudentHistoriesResponseDto result = service.getHistories(ADMIN_ID, STUDENT_ID);

        // then
        List<AdminStudentHistoryResponseDto> histories = result.histories();
        assertThat(histories).extracting(AdminStudentHistoryResponseDto::type).containsExactly(
                AdminStudentHistoryType.HELPER_CANCELED, AdminStudentHistoryType.HELPER_CANCELED,
                AdminStudentHistoryType.STUDENT_CANCELED, AdminStudentHistoryType.NO_SHOW);
        AdminStudentHistoryResponseDto first = histories.get(0);
        assertThat(first.helper().id()).isEqualTo(7L);
        assertThat(first.cancelReason()).isEqualTo(CancelReason.ACADEMIC);
        assertThat(first.minutesBeforeMeal()).isEqualTo(26 * 60L);
        assertThat(first.afterAction()).isEqualTo(ApplicationAfterAction.PROMOTED);
        assertThat(first.promotedHelper().id()).isEqualTo(8L);
        assertThat(first.promotedWaitingOrder()).isEqualTo(1);
        AdminStudentHistoryResponseDto second = histories.get(1);
        assertThat(second.cancelReasonDetail()).isEqualTo("알바 시간이 겹쳤어요");
        assertThat(second.minutesBeforeMeal()).isEqualTo(40L);
        assertThat(second.afterAction()).isEqualTo(ApplicationAfterAction.REOPENED);
        assertThat(second.promotedHelper()).isNull();
        assertThat(second.requestStatus()).isEqualTo(HelpRequestStatus.FAILED);
        AdminStudentHistoryResponseDto third = histories.get(2);
        assertThat(third.helper()).isNull();
        assertThat(third.minutesBeforeMeal()).isEqualTo(120L);
        AdminStudentHistoryResponseDto fourth = histories.get(3);
        assertThat(fourth.helper().id()).isEqualTo(10L);
        assertThat(fourth.minutesAfterCompletion()).isEqualTo(180L);
    }
}
