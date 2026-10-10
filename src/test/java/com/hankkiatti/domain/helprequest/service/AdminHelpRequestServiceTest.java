package com.hankkiatti.domain.helprequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.entity.AdminGrade;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.repository.HelpRequestApplicationCount;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.dto.response.AdminHelpRequestResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.AdminHelpRequestRow;
import com.hankkiatti.domain.helprequest.dto.response.AdminHelpRequestSummaryResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.LimitedAdminHelpRequestResponseDto;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.exception.HelpRequestErrorType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestException;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.helprequest.repository.HelpRequestStatusCount;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.support.TestAccounts;
import com.hankkiatti.support.TestHelpRequests;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminHelpRequestServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-10-12(월) 09:00
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
    private static final LocalDateTime NOON = TODAY.atTime(12, 0);
    private static final Long ADMIN_ID = 100L;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private AdminRepository adminRepository;

    private AdminHelpRequestService service;

    private final Student student = TestHelpRequests.student("60231234");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(9, 0).atZone(SEOUL).toInstant(), SEOUL);
        service = new AdminHelpRequestService(helpRequestRepository, applicationRepository, adminRepository, clock);
        ReflectionTestUtils.setField(student, "accountId", 1L);
    }

    private void givenAdmin(AdminGrade grade) {
        Admin admin = new Admin(TestAccounts.withId(ADMIN_ID, AccountRole.ADMIN, "hash", false), "김센터", grade);
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin));
    }

    private HelpRequest request(Long id, LocalDateTime startAt, LocalDateTime requestedAt) {
        HelpRequest request = TestHelpRequests.request(student, startAt);
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "createdAt", requestedAt);
        return request;
    }

    // 10번: 매칭 완료(신청 20:41 → 매칭 20:55, 도우미 7번, 예비 2명), 11번: 도우미가 빠져 예비가 승격 응답 대기
    private void givenTwoRequests() {
        HelpRequest matched = request(10L, NOON, TODAY.minusDays(1).atTime(20, 41));
        matched.match(TODAY.minusDays(1).atTime(20, 55));
        HelpRequest pending = request(11L, NOON.plusMinutes(30), TODAY.minusDays(1).atTime(18, 0));
        pending.match(TODAY.minusDays(1).atTime(18, 10));
        pending.changeHelper();
        given(helpRequestRepository.findForAdmin(TODAY.atStartOfDay(), TODAY.plusDays(8).atStartOfDay(), null, null))
                .willReturn(List.of(matched, pending));
        given(applicationRepository.countWaitingByHelpRequest(List.of(10L, 11L)))
                .willReturn(List.of(new HelpRequestApplicationCount(10L, 2)));
        Helper helper = TestProfiles.helper(TestAccounts.withId(7L, AccountRole.HELPER, "hash", false), "60230001");
        ReflectionTestUtils.setField(helper, "accountId", 7L);
        Application confirmed = new Application(matched, helper, TODAY.minusDays(1).atTime(20, 55));
        ReflectionTestUtils.setField(confirmed, "id", 31L);
        given(applicationRepository.findMatchedWithHelper(List.of(10L, 11L))).willReturn(List.of(confirmed));
        given(applicationRepository.findHelpRequestIdsAwaitingPromotion(List.of(10L, 11L))).willReturn(List.of(11L));
    }

    @Test
    void getHelpRequests_전체권한_기본기간식사순_장애유형과계산값포함() {
        // given
        givenAdmin(AdminGrade.FULL);
        givenTwoRequests();

        // when
        List<AdminHelpRequestRow> rows = service.getHelpRequests(ADMIN_ID, null, null, null, null);

        // then
        assertThat(rows).hasSize(2).allMatch(AdminHelpRequestResponseDto.class::isInstance);
        AdminHelpRequestResponseDto matched = (AdminHelpRequestResponseDto) rows.get(0);
        assertThat(matched.id()).isEqualTo(10L);
        assertThat(matched.student().disabilityType()).isEqualTo(DisabilityType.PHYSICAL);
        assertThat(matched.student().name()).isEqualTo("학생60231234");
        assertThat(matched.helper().id()).isEqualTo(7L);
        assertThat(matched.waitingCount()).isEqualTo(2);
        assertThat(matched.minutesToMatch()).isEqualTo(14L);
        assertThat(matched.promotionPending()).isFalse();
        AdminHelpRequestResponseDto pending = (AdminHelpRequestResponseDto) rows.get(1);
        assertThat(pending.helper()).isNull();
        assertThat(pending.promotionPending()).isTrue();
        assertThat(pending.helperChanged()).isTrue();
        assertThat(pending.waitingCount()).isZero();
    }

    @Test
    void getHelpRequests_제한권한_장애유형없는별도응답() {
        // given
        givenAdmin(AdminGrade.LIMITED);
        givenTwoRequests();

        // when
        List<AdminHelpRequestRow> rows = service.getHelpRequests(ADMIN_ID, null, null, null, null);

        // then — 장애 유형 필드가 아예 없는 타입이다
        assertThat(rows).allMatch(LimitedAdminHelpRequestResponseDto.class::isInstance);
        LimitedAdminHelpRequestResponseDto row = (LimitedAdminHelpRequestResponseDto) rows.get(0);
        assertThat(row.student().name()).isEqualTo("학생60231234");
        assertThat(row.student().studentNo()).isEqualTo("60231234");
        assertThat(row.helper().id()).isEqualTo(7L);
    }

    @Test
    void getHelpRequests_상태와검색어_앞뒤공백없이부분일치로넘김() {
        // given
        givenAdmin(AdminGrade.FULL);
        given(helpRequestRepository.findForAdmin(TODAY.atStartOfDay(), TODAY.plusDays(2).atStartOfDay(),
                HelpRequestStatus.RECRUITING, "%김민%")).willReturn(List.of());

        // when
        List<AdminHelpRequestRow> rows = service.getHelpRequests(ADMIN_ID, TODAY, TODAY.plusDays(1),
                HelpRequestStatus.RECRUITING, "  김민 ");

        // then — 결과가 없으면 나머지 쿼리를 하지 않는다
        assertThat(rows).isEmpty();
        verify(applicationRepository, never()).countWaitingByHelpRequest(any());
    }

    @Test
    void getHelpRequests_기간이31일을넘음_INVALID_DATE_RANGE() {
        // given
        givenAdmin(AdminGrade.FULL);

        // when & then
        assertThatThrownBy(() -> service.getHelpRequests(ADMIN_ID, TODAY, TODAY.plusDays(31), null, null))
                .isInstanceOf(HelpRequestException.class)
                .extracting("errorCode").isEqualTo(HelpRequestErrorType.INVALID_DATE_RANGE);
    }

    @Test
    void getHelpRequests_관리자프로필없음_ACCESS_DENIED() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getHelpRequests(ADMIN_ID, null, null, null, null))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
    }

    @Test
    void getSummary_신청은전체_매칭완료는매칭과이용완료합() {
        // given
        givenAdmin(AdminGrade.LIMITED);
        given(helpRequestRepository.countByStatusBetween(TODAY.atStartOfDay(), TODAY.plusDays(8).atStartOfDay()))
                .willReturn(List.of(
                        new HelpRequestStatusCount(HelpRequestStatus.MATCHED, 5),
                        new HelpRequestStatusCount(HelpRequestStatus.COMPLETED, 3),
                        new HelpRequestStatusCount(HelpRequestStatus.RECRUITING, 2),
                        new HelpRequestStatusCount(HelpRequestStatus.FAILED, 1),
                        new HelpRequestStatusCount(HelpRequestStatus.NO_SHOW, 1),
                        new HelpRequestStatusCount(HelpRequestStatus.CANCELED, 4)));

        // when
        AdminHelpRequestSummaryResponseDto summary = service.getSummary(ADMIN_ID, null, null);

        // then — 취소는 따로 숫자 없이 신청에만 들어간다
        assertThat(summary).isEqualTo(new AdminHelpRequestSummaryResponseDto(16, 8, 2, 1, 1));
    }

    @Test
    void getSummary_신청없음_모두0() {
        // given
        givenAdmin(AdminGrade.FULL);
        given(helpRequestRepository.countByStatusBetween(any(), any())).willReturn(List.of());

        // when & then
        assertThat(service.getSummary(ADMIN_ID, TODAY, TODAY))
                .isEqualTo(new AdminHelpRequestSummaryResponseDto(0, 0, 0, 0, 0));
    }
}
