package com.hankkiatti.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.application.dto.response.MyApplicationResponseDto;
import com.hankkiatti.domain.application.dto.response.MyApplicationsResponseDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.entity.MyApplicationFilter;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.support.TestHelpRequests;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MyApplicationServiceTest {

    private static final Long HELPER_ID = 7L;
    // 2026-10-12(월) 09:00
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0);

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private HelperRepository helperRepository;

    private MyApplicationService myApplicationService;

    private final Student student = TestHelpRequests.student("60231234");
    private final Helper me = TestHelpRequests.helper("60230001");
    private final Helper other = TestHelpRequests.helper("60230002");

    private long nextId = 100;

    @BeforeEach
    void setUp() {
        myApplicationService = new MyApplicationService(applicationRepository, helperRepository);
    }

    private HelpRequest request(LocalDateTime startAt) {
        HelpRequest created = TestHelpRequests.request(student, startAt);
        ReflectionTestUtils.setField(created, "id", nextId++);
        return created;
    }

    private Application waiting(HelpRequest on, Helper helper) {
        Application application = new Application(on, helper, NOW.minusDays(1));
        ReflectionTestUtils.setField(application, "id", nextId++);
        return application;
    }

    private Application matched(HelpRequest on) {
        Application application = waiting(on, me);
        application.match(NOW.minusDays(1));
        return application;
    }

    private void givenMine(Application... mine) {
        given(helperRepository.existsById(HELPER_ID)).willReturn(true);
        given(applicationRepository.findMineWithHelpRequest(HELPER_ID)).willReturn(List.of(mine));
    }

    private static List<Long> ids(List<MyApplicationResponseDto> cards) {
        return cards.stream().map(MyApplicationResponseDto::applicationId).toList();
    }

    @Test
    void getMyApplications_여러상태_진행중은가까운순지난활동은최근순() {
        // given
        Application tomorrow = matched(request(NOW.plusDays(1)));
        Application later = waiting(request(NOW.plusDays(3)), me);
        Application pending = waiting(request(NOW.plusDays(2)), me);
        pending.promote(NOW, NOW.plusMinutes(30));
        Application lastWeek = matched(request(NOW.minusDays(7)));
        lastWeek.complete();
        Application yesterday = waiting(request(NOW.minusDays(1)), me);
        yesterday.expire();
        givenMine(later, lastWeek, tomorrow, yesterday, pending);
        given(applicationRepository.findWaitingIn(List.of(later.getHelpRequest().getId()))).willReturn(List.of(later));

        // when
        MyApplicationsResponseDto result = myApplicationService.getMyApplications(HELPER_ID, MyApplicationFilter.ALL);

        // then
        assertThat(ids(result.inProgress())).containsExactly(tomorrow.getId(), pending.getId(), later.getId());
        assertThat(ids(result.past())).containsExactly(yesterday.getId(), lastWeek.getId());
    }

    @Test
    void getMyApplications_매칭완료만_장애학생이름과카톡ID() {
        // given
        Application matchedCard = matched(request(NOW.plusDays(1)));
        Application waitingCard = waiting(request(NOW.plusDays(2)), me);
        Application pending = waiting(request(NOW.plusDays(3)), me);
        pending.promote(NOW, NOW.plusMinutes(30));
        Application completed = matched(request(NOW.minusDays(1)));
        completed.complete();
        givenMine(matchedCard, waitingCard, pending, completed);
        given(applicationRepository.findWaitingIn(any())).willReturn(List.of(waitingCard));

        // when
        MyApplicationsResponseDto result = myApplicationService.getMyApplications(HELPER_ID, MyApplicationFilter.ALL);

        // then
        assertThat(result.inProgress().get(0).student().name()).isEqualTo("학생60231234");
        assertThat(result.inProgress().get(0).student().kakaoId()).isEqualTo("kakao60231234");
        assertThat(result.inProgress().get(1).student()).isNull();
        assertThat(result.inProgress().get(2).student()).isNull();
        assertThat(result.past().get(0).student()).isNull();
    }

    @Test
    void getMyApplications_예비_그신청예비중지원순번() {
        // given — 첫 신청은 다른 도우미 2명 뒤라 3번, 둘째 신청은 혼자라 1번
        HelpRequest crowded = request(NOW.plusDays(1));
        HelpRequest quiet = request(NOW.plusDays(2));
        Application third = waiting(crowded, me);
        Application only = waiting(quiet, me);
        givenMine(third, only);
        given(applicationRepository.findWaitingIn(List.of(crowded.getId(), quiet.getId()))).willReturn(List.of(
                waiting(crowded, other), waiting(crowded, other), third, only));

        // when
        MyApplicationsResponseDto result = myApplicationService.getMyApplications(HELPER_ID, MyApplicationFilter.ALL);

        // then
        assertThat(result.inProgress()).extracting(MyApplicationResponseDto::waitingOrder).containsExactly(3, 1);
        assertThat(result.inProgress()).extracting(MyApplicationResponseDto::status)
                .containsOnly(ApplicationStatus.WAITING);
    }

    @Test
    void getMyApplications_지난활동_내취소는사유와이용완료노쇼는봉사시간() {
        // given
        Application canceled = matched(request(NOW.minusDays(1)));
        canceled.cancelByHelper(CancelReason.ILLNESS, null, NOW.minusDays(2));
        Application completed = matched(request(NOW.minusDays(2)));
        completed.complete();
        Application noShow = matched(request(NOW.minusDays(3)));
        noShow.complete();
        noShow.markNoShow();
        Application studentCanceled = matched(request(NOW.minusDays(4)));
        studentCanceled.cancelByStudent();
        givenMine(canceled, completed, noShow, studentCanceled);

        // when
        MyApplicationsResponseDto result = myApplicationService.getMyApplications(HELPER_ID, MyApplicationFilter.ALL);

        // then
        assertThat(result.inProgress()).isEmpty();
        assertThat(result.past()).extracting(MyApplicationResponseDto::cancelReason)
                .containsExactly(CancelReason.ILLNESS, null, null, null);
        assertThat(result.past()).extracting(MyApplicationResponseDto::volunteerHours)
                .containsExactly(null, new BigDecimal("1.0"), new BigDecimal("0.0"), null);
        assertThat(result.past()).extracting(MyApplicationResponseDto::waitingOrder).containsOnlyNulls();
        verify(applicationRepository, never()).findWaitingIn(any());
    }

    @Test
    void getMyApplications_빠짐과승격거절_지난활동에그대로() {
        // given
        Application withdrawn = waiting(request(NOW.minusDays(1)), me);
        withdrawn.withdraw(NOW.minusDays(2));
        Application declined = waiting(request(NOW.minusDays(2)), me);
        declined.promote(NOW.minusDays(3), NOW.minusDays(3).plusMinutes(30));
        declined.declinePromotion(NOW.minusDays(3));
        Application excluded = waiting(request(NOW.minusDays(3)), me);
        excluded.exclude();
        givenMine(withdrawn, declined, excluded);

        // when
        MyApplicationsResponseDto result = myApplicationService.getMyApplications(HELPER_ID, MyApplicationFilter.ALL);

        // then
        assertThat(result.past()).extracting(MyApplicationResponseDto::status).containsExactly(
                ApplicationStatus.WITHDRAWN, ApplicationStatus.PROMOTION_DECLINED, ApplicationStatus.EXCLUDED);
    }

    @Test
    void getMyApplications_매칭필터_진행중매칭완료만() {
        // given
        Application matchedCard = matched(request(NOW.plusDays(1)));
        Application waitingCard = waiting(request(NOW.plusDays(2)), me);
        Application completed = matched(request(NOW.minusDays(1)));
        completed.complete();
        givenMine(matchedCard, waitingCard, completed);

        // when
        MyApplicationsResponseDto result =
                myApplicationService.getMyApplications(HELPER_ID, MyApplicationFilter.MATCHED);

        // then
        assertThat(ids(result.inProgress())).containsExactly(matchedCard.getId());
        assertThat(result.past()).isEmpty();
        verify(applicationRepository, never()).findWaitingIn(any());
    }

    @Test
    void getMyApplications_예비필터_예비와승격응답대기() {
        // given
        Application matchedCard = matched(request(NOW.plusDays(1)));
        Application waitingCard = waiting(request(NOW.plusDays(2)), me);
        Application pending = waiting(request(NOW.plusDays(3)), me);
        pending.promote(NOW, NOW.plusMinutes(30));
        Application expired = waiting(request(NOW.minusDays(1)), me);
        expired.expire();
        givenMine(matchedCard, waitingCard, pending, expired);
        given(applicationRepository.findWaitingIn(List.of(waitingCard.getHelpRequest().getId())))
                .willReturn(List.of(waitingCard));

        // when
        MyApplicationsResponseDto result =
                myApplicationService.getMyApplications(HELPER_ID, MyApplicationFilter.WAITING);

        // then
        assertThat(ids(result.inProgress())).containsExactly(waitingCard.getId(), pending.getId());
        assertThat(result.inProgress().get(0).waitingOrder()).isEqualTo(1);
        assertThat(result.past()).isEmpty();
    }

    @Test
    void getMyApplications_지난활동필터_진행중은빈목록() {
        // given
        Application matchedCard = matched(request(NOW.plusDays(1)));
        Application completed = matched(request(NOW.minusDays(1)));
        completed.complete();
        givenMine(matchedCard, completed);

        // when
        MyApplicationsResponseDto result = myApplicationService.getMyApplications(HELPER_ID, MyApplicationFilter.PAST);

        // then
        assertThat(result.inProgress()).isEmpty();
        assertThat(ids(result.past())).containsExactly(completed.getId());
    }

    @Test
    void getMyApplications_지원없음_빈두목록() {
        // given
        givenMine();

        // when
        MyApplicationsResponseDto result = myApplicationService.getMyApplications(HELPER_ID, MyApplicationFilter.ALL);

        // then
        assertThat(result.inProgress()).isEmpty();
        assertThat(result.past()).isEmpty();
        verify(applicationRepository, never()).findWaitingIn(any());
    }

    @Test
    void getMyApplications_도우미아님_접근거부() {
        // given
        given(helperRepository.existsById(HELPER_ID)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> myApplicationService.getMyApplications(HELPER_ID, MyApplicationFilter.ALL))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
        verify(applicationRepository, never()).findMineWithHelpRequest(any());
    }
}
