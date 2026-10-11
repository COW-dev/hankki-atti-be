package com.hankkiatti.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.support.TestHelpRequests;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HelperDeactivationCancelerTest {

    // 2026-10-12(월) 12:00 식사, 지금은 하루 전
    private static final LocalDateTime MEAL = LocalDateTime.of(2026, 10, 12, 12, 0);
    private static final LocalDateTime NOW = MEAL.minusDays(1);
    private static final Long APPLICATION_ID = 20L;
    private static final Long REQUEST_ID = 10L;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private WaitingPromoter waitingPromoter;

    private HelperDeactivationCanceler canceler;
    private HelpRequest request;
    private Application application;

    @BeforeEach
    void setUp() {
        canceler = new HelperDeactivationCanceler(applicationRepository, helpRequestRepository, waitingPromoter);
        request = TestHelpRequests.request(TestHelpRequests.student("60231234"), MEAL);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        Helper helper = TestHelpRequests.helper("60230007");
        application = new Application(request, helper, NOW.minusDays(1));
        ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
    }

    private void givenLocked() {
        given(applicationRepository.findHelpRequestIdById(APPLICATION_ID)).willReturn(Optional.of(REQUEST_ID));
        given(helpRequestRepository.findByIdForUpdate(REQUEST_ID)).willReturn(Optional.of(request));
        given(applicationRepository.findByIdForUpdate(APPLICATION_ID)).willReturn(Optional.of(application));
    }

    @Test
    void release_매칭완료_관리자비활성화사유로취소하고예비승격을기록한다() {
        // given
        request.match(NOW.minusDays(1));
        application.match(NOW.minusDays(1));
        givenLocked();
        given(waitingPromoter.promoteOrReopen(request, NOW)).willReturn(ApplicationAfterAction.PROMOTED);

        // when
        Optional<ApplicationStatus> released = canceler.release(APPLICATION_ID, NOW);

        // then
        assertThat(released).contains(ApplicationStatus.MATCHED);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.HELPER_CANCELED);
        assertThat(application.getCancelReason()).isEqualTo(CancelReason.ADMIN_DEACTIVATED);
        assertThat(application.getCanceledAt()).isEqualTo(NOW);
        assertThat(application.getAfterAction()).isEqualTo(ApplicationAfterAction.PROMOTED);
    }

    @Test
    void release_승격응답대기_도우미취소로바꾸고다음예비로넘긴다() {
        // given
        request.match(NOW.minusDays(1));
        application.promote(NOW.minusHours(1), MEAL.minusMinutes(15), null, 1);
        givenLocked();
        given(waitingPromoter.promoteOrReopen(request, NOW)).willReturn(ApplicationAfterAction.REOPENED);

        // when
        Optional<ApplicationStatus> released = canceler.release(APPLICATION_ID, NOW);

        // then
        assertThat(released).contains(ApplicationStatus.PROMOTION_PENDING);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.HELPER_CANCELED);
        assertThat(application.getAfterAction()).isEqualTo(ApplicationAfterAction.REOPENED);
    }

    @Test
    void release_예비_자리에서빠지고승격은하지않는다() {
        // given
        request.match(NOW.minusDays(1));
        givenLocked();

        // when
        Optional<ApplicationStatus> released = canceler.release(APPLICATION_ID, NOW);

        // then
        assertThat(released).contains(ApplicationStatus.WAITING);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        verify(waitingPromoter, never()).promoteOrReopen(any(), any());
    }

    @Test
    void release_그사이끝난지원_건너뛴다() {
        // given
        request.match(NOW.minusDays(1));
        application.withdraw(NOW.minusHours(2));
        givenLocked();

        // when
        Optional<ApplicationStatus> released = canceler.release(APPLICATION_ID, NOW);

        // then
        assertThat(released).isEmpty();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        verify(waitingPromoter, never()).promoteOrReopen(any(), any());
    }

    @Test
    void release_식사가시작된매칭_건너뛴다() {
        // given
        request.match(NOW.minusDays(1));
        application.match(NOW.minusDays(1));
        givenLocked();

        // when
        Optional<ApplicationStatus> released = canceler.release(APPLICATION_ID, MEAL);

        // then
        assertThat(released).isEmpty();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.MATCHED);
    }

    @Test
    void release_없는지원_건너뛴다() {
        // given
        given(applicationRepository.findHelpRequestIdById(APPLICATION_ID)).willReturn(Optional.empty());

        // when & then
        assertThat(canceler.release(APPLICATION_ID, NOW)).isEmpty();
        verify(helpRequestRepository, never()).findByIdForUpdate(any());
    }
}
