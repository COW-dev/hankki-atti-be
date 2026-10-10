package com.hankkiatti.domain.helper.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.repository.HelperApplicationCount;
import com.hankkiatti.domain.helper.dto.response.AdminHelperActivityResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperDetailResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelpersResponseDto;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.exception.HelperErrorType;
import com.hankkiatti.domain.helper.exception.HelperException;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.support.TestAccounts;
import com.hankkiatti.support.TestHelpRequests;
import com.hankkiatti.support.TestProfiles;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminHelperServiceTest {

    // 2026-10-12(월) 12:00
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 12, 12, 0);
    private static final Long HELPER_ID = 7L;

    @Mock
    private HelperRepository helperRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    private AdminHelperService service;

    private Student student;
    private Helper helper;
    private long nextId = 100;

    @BeforeEach
    void setUp() {
        service = new AdminHelperService(helperRepository, applicationRepository);
        student = TestHelpRequests.student("60231234");
        ReflectionTestUtils.setField(student, "accountId", 1L);
        ReflectionTestUtils.setField(student, "name", "김민지");
        helper = helper(HELPER_ID, "60230007", "010-3333-4444");
    }

    private Helper helper(Long id, String studentNo, String phone) {
        Helper created = TestProfiles.helper(TestAccounts.withId(id, AccountRole.HELPER, "hash", false), studentNo);
        ReflectionTestUtils.setField(created, "accountId", id);
        ReflectionTestUtils.setField(created, "phone", phone);
        ReflectionTestUtils.setField(created, "createdAt", NOON.minusDays(30));
        return created;
    }

    private HelpRequest request(LocalDateTime startAt) {
        HelpRequest request = TestHelpRequests.request(student, startAt);
        ReflectionTestUtils.setField(request, "id", nextId++);
        return request;
    }

    private Application application(HelpRequest request, Helper of) {
        Application application = new Application(request, of, request.getStartAt().minusDays(1));
        ReflectionTestUtils.setField(application, "id", nextId++);
        return application;
    }

    private Application matched(Helper of, LocalDateTime startAt) {
        HelpRequest request = request(startAt);
        request.match(startAt.minusDays(1));
        Application application = application(request, of);
        application.match(startAt.minusDays(1));
        return application;
    }

    @Test
    void getHelpers_검색어필터페이지_전화번호를가리고이용완료건수를붙인다() {
        // given
        Helper other = helper(8L, "60230008", "011-123-4567");
        Page<Helper> page = new PageImpl<>(List.of(helper, other), PageRequest.of(1, AdminHelperService.PAGE_SIZE), 22);
        given(helperRepository.searchForAdmin(eq("%윤태%"), eq(AccountStatus.ACTIVE), eq(true),
                eq(PageRequest.of(1, AdminHelperService.PAGE_SIZE)))).willReturn(page);
        given(applicationRepository.countCompletedByHelper(List.of(HELPER_ID, 8L)))
                .willReturn(List.of(new HelperApplicationCount(HELPER_ID, 2)));

        // when
        AdminHelpersResponseDto result = service.getHelpers("  윤태 ", AccountStatus.ACTIVE, true, 1);

        // then
        assertThat(result.helpers()).extracting("helperId").containsExactly(HELPER_ID, 8L);
        assertThat(result.helpers()).extracting("maskedPhone").containsExactly("010-****-4444", "011-***-4567");
        assertThat(result.helpers()).extracting("completedCount").containsExactly(2L, 0L);
        assertThat(result.helpers().get(0).status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.helpers().get(0).joinedAt()).isEqualTo(NOON.minusDays(30));
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalCount()).isEqualTo(22);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void getHelpers_검색어없고결과없음_조건없이조회하고건수조회는안함() {
        // given
        given(helperRepository.searchForAdmin(null, null, null, PageRequest.of(0, AdminHelperService.PAGE_SIZE)))
                .willReturn(Page.empty());

        // when
        AdminHelpersResponseDto result = service.getHelpers(" ", null, null, 0);

        // then
        assertThat(result.helpers()).isEmpty();
        assertThat(result.totalCount()).isZero();
        verify(applicationRepository, never()).countCompletedByHelper(any());
    }

    @Test
    void getHelpers_음수페이지_INVALID_PAGE() {
        // when & then
        assertThatThrownBy(() -> service.getHelpers(null, null, null, -1))
                .isInstanceOf(HelperException.class)
                .extracting("errorCode").isEqualTo(HelperErrorType.INVALID_PAGE);
        verify(helperRepository, never()).searchForAdmin(any(), any(), any(), any());
    }

    @Test
    void getHelper_활동요약_관리자비활성화취소는취소건수에서뺀다() {
        // given
        Application completed = matched(helper, NOON.minusDays(10));
        completed.complete();
        Application noShow = matched(helper, NOON.minusDays(9));
        noShow.complete();
        noShow.markNoShow();
        Application canceled = matched(helper, NOON.minusDays(8));
        canceled.cancelByHelper(CancelReason.ILLNESS, null, NOON.minusDays(9));
        Application deactivated = matched(helper, NOON.plusDays(1));
        deactivated.cancelByDeactivation(NOON);
        Application stillMatched = matched(helper, NOON.plusDays(2));
        Application waitingOnly = application(request(NOON.plusDays(3)), helper);
        given(helperRepository.findById(HELPER_ID)).willReturn(Optional.of(helper));
        given(applicationRepository.findByHelperAccountId(HELPER_ID))
                .willReturn(List.of(completed, noShow, canceled, deactivated, stillMatched, waitingOnly));

        // when
        AdminHelperDetailResponseDto detail = service.getHelper(HELPER_ID);

        // then
        assertThat(detail.phone()).isEqualTo("010-3333-4444");
        assertThat(detail.attiMember()).isTrue();
        assertThat(detail.summary().matchedCount()).isEqualTo(5);
        assertThat(detail.summary().completedCount()).isEqualTo(1);
        assertThat(detail.summary().volunteerHours()).isEqualByComparingTo(new BigDecimal("1.0"));
        assertThat(detail.summary().canceledCount()).isEqualTo(1);
        assertThat(detail.summary().noShowCount()).isEqualTo(1);
    }

    @Test
    void getHelper_없는도우미_NOT_FOUND() {
        // given
        given(helperRepository.findById(HELPER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getHelper(HELPER_ID))
                .isInstanceOf(HelperException.class)
                .extracting("errorCode").isEqualTo(HelperErrorType.NOT_FOUND);
        verify(applicationRepository, never()).findByHelperAccountId(any());
    }

    @Test
    void getActivities_매칭되거나승격된지원만_최근식사부터_이후처리와분() {
        // given
        LocalDateTime canceledAt = NOON.minusHours(26);
        // ① 학사 일정으로 취소(식사 26시간 전) → 예비 1번 도우미 8 승격
        Application promotedAway = matched(helper, NOON);
        promotedAway.cancelByHelper(CancelReason.ACADEMIC, null, canceledAt);
        promotedAway.recordAfterAction(ApplicationAfterAction.PROMOTED);
        Application promoted = application(promotedAway.getHelpRequest(), helper(8L, "60230008", "010-8888-8888"));
        promoted.promote(canceledAt, null, null, 1);
        // ② 기타 사유로 취소(식사 50분 전) → 모집 재개 → 다른 도우미 매칭
        Application reopened = matched(helper, NOON.minusDays(4));
        reopened.cancelByHelper(CancelReason.OTHER, "수업 보강", NOON.minusDays(4).minusMinutes(50));
        reopened.recordAfterAction(ApplicationAfterAction.REOPENED);
        reopened.getHelpRequest().reopen();
        reopened.getHelpRequest().match(NOON.minusDays(4).minusMinutes(30));
        // ③ 노쇼 (이용 완료 3시간 뒤 신고)
        Application noShow = matched(helper, NOON.minusDays(2));
        noShow.complete();
        noShow.markNoShow();
        noShow.getHelpRequest().complete(NOON.minusDays(2).plusHours(1));
        noShow.getHelpRequest().reportNoShow(NOON.minusDays(2).plusHours(4));
        // ④ 매칭 뒤 장애학생 취소
        Application studentCanceled = matched(helper, NOON.minusDays(1));
        studentCanceled.cancelByStudent();
        studentCanceled.getHelpRequest().cancelByStudent(NOON.minusDays(1).minusHours(2));
        // ⑤ 예비로만 있다 빠짐 — 넣지 않는다
        Application withdrawn = application(request(NOON.minusDays(3)), helper);
        withdrawn.withdraw(NOON.minusDays(4));
        given(helperRepository.findById(HELPER_ID)).willReturn(Optional.of(helper));
        given(applicationRepository.findMineWithHelpRequest(HELPER_ID))
                .willReturn(List.of(reopened, withdrawn, noShow, promotedAway, studentCanceled));
        given(applicationRepository.findWithHelperByHelpRequestIdIn(List.of(promotedAway.getHelpRequest().getId())))
                .willReturn(List.of(promotedAway, promoted));

        // when
        List<AdminHelperActivityResponseDto> activities = service.getActivities(HELPER_ID);

        // then
        assertThat(activities).extracting("applicationId").containsExactly(
                promotedAway.getId(), studentCanceled.getId(), noShow.getId(), reopened.getId());

        AdminHelperActivityResponseDto first = activities.get(0);
        assertThat(first.status()).isEqualTo(ApplicationStatus.HELPER_CANCELED);
        assertThat(first.studentName()).isEqualTo("김민지");
        assertThat(first.cancelReason()).isEqualTo(CancelReason.ACADEMIC);
        assertThat(first.minutesBeforeMeal()).isEqualTo(26 * 60);
        assertThat(first.afterAction()).isEqualTo(ApplicationAfterAction.PROMOTED);
        assertThat(first.promotedWaitingOrder()).isEqualTo(1);

        AdminHelperActivityResponseDto student = activities.get(1);
        assertThat(student.status()).isEqualTo(ApplicationStatus.STUDENT_CANCELED);
        assertThat(student.canceledAt()).isEqualTo(NOON.minusDays(1).minusHours(2));
        assertThat(student.minutesBeforeMeal()).isNull();

        AdminHelperActivityResponseDto noShowRow = activities.get(2);
        assertThat(noShowRow.minutesAfterCompletion()).isEqualTo(180);

        AdminHelperActivityResponseDto last = activities.get(3);
        assertThat(last.cancelReasonDetail()).isEqualTo("수업 보강");
        assertThat(last.minutesBeforeMeal()).isEqualTo(50);
        assertThat(last.afterAction()).isEqualTo(ApplicationAfterAction.REOPENED);
        assertThat(last.promotedWaitingOrder()).isNull();
        assertThat(last.requestStatus()).isEqualTo(HelpRequestStatus.MATCHED);
    }

    @Test
    void getActivities_승격거절_승격된지원이라넣는다() {
        // given
        Application declined = application(request(NOON), helper);
        declined.promote(NOON.minusMinutes(50), NOON.minusMinutes(15), null, 1);
        declined.declinePromotion(NOON.minusMinutes(40));
        given(helperRepository.findById(HELPER_ID)).willReturn(Optional.of(helper));
        given(applicationRepository.findMineWithHelpRequest(HELPER_ID)).willReturn(List.of(declined));

        // when
        List<AdminHelperActivityResponseDto> activities = service.getActivities(HELPER_ID);

        // then
        assertThat(activities).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo(ApplicationStatus.PROMOTION_DECLINED);
            assertThat(row.canceledAt()).isEqualTo(NOON.minusMinutes(40));
        });
        verify(applicationRepository, never()).findWithHelperByHelpRequestIdIn(any());
    }

    @Test
    void getActivities_없는도우미_NOT_FOUND() {
        // given
        given(helperRepository.findById(HELPER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getActivities(HELPER_ID))
                .isInstanceOf(HelperException.class)
                .extracting("errorCode").isEqualTo(HelperErrorType.NOT_FOUND);
    }
}
