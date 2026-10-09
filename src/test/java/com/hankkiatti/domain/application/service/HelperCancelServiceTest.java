package com.hankkiatti.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.application.dto.request.HelperCancelRequestDto;
import com.hankkiatti.domain.application.dto.response.HelperCancelResponseDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.support.TestAccounts;
import com.hankkiatti.support.TestHelpRequests;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
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
class HelperCancelServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-10-12(월) 09:00
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0);
    private static final LocalDateTime NOON = NOW.withHour(12);
    private static final Long REQUEST_ID = 10L;
    private static final Long MY_APPLICATION_ID = 31L;
    private static final HelperCancelRequestDto ILLNESS = new HelperCancelRequestDto(CancelReason.ILLNESS, null);

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private HelperRepository helperRepository;

    private HelperCancelService helperCancelService;

    private final Student student = TestHelpRequests.student("60231234");
    private final Helper me = helper(7L, "60230001");
    private final Helper first = helper(8L, "60230002");
    private final Helper second = helper(9L, "60230003");

    private HelpRequest request;
    private Application mine;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        helperCancelService = new HelperCancelService(applicationRepository, helpRequestRepository, helperRepository,
                new ApplyPolicy(), clock);
        request = request(REQUEST_ID, NOON);
        request.match(NOW.minusDays(1));
        mine = application(MY_APPLICATION_ID, request, me);
        mine.match(NOW.minusDays(1));
    }

    // accountId는 저장할 때 @MapsId가 채우므로 단위 테스트에서는 직접 넣는다
    private static Helper helper(Long id, String studentNo) {
        Helper helper = TestProfiles.helper(TestAccounts.withId(id, AccountRole.HELPER, "hash", false), studentNo);
        ReflectionTestUtils.setField(helper, "accountId", id);
        return helper;
    }

    private HelpRequest request(Long id, LocalDateTime startAt) {
        HelpRequest created = TestHelpRequests.request(student, startAt);
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    private static Application application(Long id, HelpRequest on, Helper helper) {
        Application application = new Application(on, helper, NOW.minusDays(1).plusMinutes(id));
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }

    // 내 지원·신청을 잠근 상태까지 (검증에서 막히는 경우)
    private void givenMyApplicationLocked() {
        given(applicationRepository.findHelpRequestIdByIdAndHelperId(MY_APPLICATION_ID, 7L))
                .willReturn(Optional.of(REQUEST_ID));
        given(helpRequestRepository.findByIdForUpdate(REQUEST_ID)).willReturn(Optional.of(request));
        given(applicationRepository.findByIdForUpdate(MY_APPLICATION_ID)).willReturn(Optional.of(mine));
    }

    // 취소까지 가서 예비를 보는 경우
    private void givenMyMatchedApplication(Application... waiting) {
        givenMyApplicationLocked();
        given(applicationRepository.findWaitingForUpdate(REQUEST_ID)).willReturn(List.of(waiting));
    }

    private void givenCandidateActive(Helper candidate, Application... active) {
        given(helperRepository.findByIdForUpdate(candidate.getAccountId())).willReturn(Optional.of(candidate));
        given(applicationRepository.findActiveWithHelpRequestByHelperId(candidate.getAccountId()))
                .willReturn(List.of(active));
    }

    private void assertApplicationError(Runnable call, ApplicationErrorType expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    @Test
    void cancel_예비있음_1번이매칭으로승격되고도우미바뀜() {
        // given
        Application waitingFirst = application(41L, request, first);
        Application waitingSecond = application(42L, request, second);
        givenMyMatchedApplication(waitingFirst, waitingSecond);
        givenCandidateActive(first, waitingFirst);

        // when
        HelperCancelResponseDto result = helperCancelService.cancel(7L, MY_APPLICATION_ID, ILLNESS);

        // then
        assertThat(result.status()).isEqualTo(ApplicationStatus.HELPER_CANCELED);
        assertThat(result.cancelReason()).isEqualTo(CancelReason.ILLNESS);
        assertThat(result.canceledAt()).isEqualTo(NOW);
        assertThat(mine.getAfterAction()).isEqualTo(ApplicationAfterAction.PROMOTED);
        assertThat(waitingFirst.getStatus()).isEqualTo(ApplicationStatus.MATCHED);
        assertThat(waitingFirst.getPromotedAt()).isEqualTo(NOW);
        assertThat(waitingSecond.getStatus()).isEqualTo(ApplicationStatus.WAITING);
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.MATCHED);
        assertThat(request.isHelperChanged()).isTrue();
    }

    @Test
    void cancel_예비없음_모집재개() {
        // given
        givenMyMatchedApplication();

        // when
        helperCancelService.cancel(7L, MY_APPLICATION_ID, ILLNESS);

        // then
        assertThat(mine.getStatus()).isEqualTo(ApplicationStatus.HELPER_CANCELED);
        assertThat(mine.getAfterAction()).isEqualTo(ApplicationAfterAction.REOPENED);
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.RECRUITING);
        assertThat(request.isHelperChanged()).isFalse();
    }

    @Test
    void cancel_예비1번이다른매칭과겹침_1번은자동제외되고2번승격() {
        // given — 1번은 예비로 있는 사이 12:30 다른 신청에 매칭됐다
        Application waitingFirst = application(41L, request, first);
        Application waitingSecond = application(42L, request, second);
        HelpRequest other = request(20L, NOON.plusMinutes(30));
        other.match(NOW.minusHours(2));
        Application firstMatchedElsewhere = application(50L, other, first);
        firstMatchedElsewhere.match(NOW.minusHours(2));
        givenMyMatchedApplication(waitingFirst, waitingSecond);
        givenCandidateActive(first, waitingFirst, firstMatchedElsewhere);
        givenCandidateActive(second, waitingSecond);

        // when
        helperCancelService.cancel(7L, MY_APPLICATION_ID, ILLNESS);

        // then
        assertThat(waitingFirst.getStatus()).isEqualTo(ApplicationStatus.EXCLUDED);
        assertThat(waitingSecond.getStatus()).isEqualTo(ApplicationStatus.MATCHED);
        assertThat(mine.getAfterAction()).isEqualTo(ApplicationAfterAction.PROMOTED);
    }

    @Test
    void cancel_예비가모두다른매칭과겹침_전부제외되고모집재개() {
        // given
        Application waitingFirst = application(41L, request, first);
        HelpRequest other = request(20L, NOON);
        Application firstMatchedElsewhere = application(50L, other, first);
        firstMatchedElsewhere.match(NOW.minusHours(2));
        givenMyMatchedApplication(waitingFirst);
        givenCandidateActive(first, waitingFirst, firstMatchedElsewhere);

        // when
        helperCancelService.cancel(7L, MY_APPLICATION_ID, ILLNESS);

        // then
        assertThat(waitingFirst.getStatus()).isEqualTo(ApplicationStatus.EXCLUDED);
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.RECRUITING);
        assertThat(mine.getAfterAction()).isEqualTo(ApplicationAfterAction.REOPENED);
    }

    @Test
    void cancel_기타사유는내용을다듬어저장하고_다른사유는내용을버린다() {
        // given
        givenMyMatchedApplication();

        // when
        helperCancelService.cancel(7L, MY_APPLICATION_ID,
                new HelperCancelRequestDto(CancelReason.OTHER, "  갑자기 시험 일정이 생겼어요 "));

        // then
        assertThat(mine.getCancelReasonDetail()).isEqualTo("갑자기 시험 일정이 생겼어요");
    }

    @Test
    void cancel_기타사유인데내용없음_CANCEL_REASON_DETAIL_REQUIRED() {
        // given
        givenMyApplicationLocked();

        // when & then
        assertApplicationError(() -> helperCancelService.cancel(7L, MY_APPLICATION_ID,
                new HelperCancelRequestDto(CancelReason.OTHER, "   ")), ApplicationErrorType.CANCEL_REASON_DETAIL_REQUIRED);
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.MATCHED);
    }

    @Test
    void cancel_관리자비활성화사유_INVALID_CANCEL_REASON() {
        // given
        givenMyApplicationLocked();

        // when & then
        assertApplicationError(() -> helperCancelService.cancel(7L, MY_APPLICATION_ID,
                new HelperCancelRequestDto(CancelReason.ADMIN_DEACTIVATED, null)), ApplicationErrorType.INVALID_CANCEL_REASON);
    }

    @Test
    void cancel_남의지원이거나없는지원_NOT_FOUND() {
        // given
        given(applicationRepository.findHelpRequestIdByIdAndHelperId(MY_APPLICATION_ID, 99L))
                .willReturn(Optional.empty());

        // when & then
        assertApplicationError(() -> helperCancelService.cancel(99L, MY_APPLICATION_ID, ILLNESS),
                ApplicationErrorType.NOT_FOUND);
    }

    @Test
    void cancel_이미취소한지원_INVALID_STATUS() {
        // given
        mine.cancelByHelper(CancelReason.ILLNESS, null, NOW.minusHours(1));
        givenMyApplicationLocked();

        // when & then
        assertApplicationError(() -> helperCancelService.cancel(7L, MY_APPLICATION_ID, ILLNESS),
                ApplicationErrorType.INVALID_STATUS);
    }

    @Test
    void cancel_식사시작뒤_MEAL_STARTED() {
        // given — 시작 = 지금
        request = request(REQUEST_ID, NOW);
        request.match(NOW.minusDays(1));
        mine = application(MY_APPLICATION_ID, request, me);
        mine.match(NOW.minusDays(1));
        givenMyApplicationLocked();

        // when & then
        assertApplicationError(() -> helperCancelService.cancel(7L, MY_APPLICATION_ID, ILLNESS),
                ApplicationErrorType.MEAL_STARTED);
        assertThat(mine.getStatus()).isEqualTo(ApplicationStatus.MATCHED);
    }
}
