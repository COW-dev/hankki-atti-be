package com.hankkiatti.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WaitingPromoterTest {

    // 2026-10-12(월) 12:00 식사
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 12, 12, 0);
    private static final Long REQUEST_ID = 10L;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private HelperRepository helperRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WaitingPromoter waitingPromoter;

    private HelpRequest request;
    private Helper candidate;
    private Application waiting;

    @BeforeEach
    void setUp() {
        waitingPromoter = new WaitingPromoter(applicationRepository, helperRepository, new ApplyPolicy(),
                eventPublisher);
        request = TestHelpRequests.request(TestHelpRequests.student("60231234"), NOON);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        request.match(NOON.minusDays(1));
        candidate = TestProfiles.helper(TestAccounts.withId(8L, AccountRole.HELPER, "hash", false), "60230002");
        ReflectionTestUtils.setField(candidate, "accountId", 8L);
        waiting = new Application(request, candidate, NOON.minusDays(1));
        ReflectionTestUtils.setField(waiting, "id", 32L);
    }

    private void givenCandidateWithNoOtherMatch() {
        given(applicationRepository.findWaitingForUpdate(REQUEST_ID)).willReturn(List.of(waiting));
        given(helperRepository.findByIdForUpdate(8L)).willReturn(Optional.of(candidate));
        given(applicationRepository.findActiveWithHelpRequestByHelperId(8L)).willReturn(List.of(waiting));
    }

    @Test
    void responseDeadline_1시간보다많이남음_응답필요없음() {
        assertThat(WaitingPromoter.responseDeadline(NOON, NOON.minusMinutes(61))).isNull();
    }

    @Test
    void responseDeadline_정확히1시간전_식사15분전까지() {
        assertThat(WaitingPromoter.responseDeadline(NOON, NOON.minusHours(1))).isEqualTo(NOON.minusMinutes(15));
    }

    @Test
    void responseDeadline_식사20분전_식사15분전까지() {
        assertThat(WaitingPromoter.responseDeadline(NOON, NOON.minusMinutes(20))).isEqualTo(NOON.minusMinutes(15));
    }

    @Test
    void responseDeadline_정확히15분전이후_식사시작까지() {
        assertThat(WaitingPromoter.responseDeadline(NOON, NOON.minusMinutes(15))).isEqualTo(NOON);
        assertThat(WaitingPromoter.responseDeadline(NOON, NOON.minusMinutes(2))).isEqualTo(NOON);
    }

    @Test
    void promoteOrReopen_1시간이내_응답대기로승격하고겹침제외이벤트는보내지않음() {
        // given
        givenCandidateWithNoOtherMatch();

        // when
        ApplicationAfterAction result = waitingPromoter.promoteOrReopen(request, NOON.minusMinutes(40));

        // then
        assertThat(result).isEqualTo(ApplicationAfterAction.PROMOTED);
        assertThat(waiting.getStatus()).isEqualTo(ApplicationStatus.PROMOTION_PENDING);
        assertThat(waiting.getPromotionDeadline()).isEqualTo(NOON.minusMinutes(15));
        assertThat(request.isHelperChanged()).isTrue();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void promoteOrReopen_1시간넘게남음_바로매칭하고겹침제외이벤트() {
        // given
        givenCandidateWithNoOtherMatch();

        // when
        ApplicationAfterAction result = waitingPromoter.promoteOrReopen(request, NOON.minusHours(3));

        // then
        assertThat(result).isEqualTo(ApplicationAfterAction.PROMOTED);
        assertThat(waiting.getStatus()).isEqualTo(ApplicationStatus.MATCHED);
        assertThat(waiting.getPromotionDeadline()).isNull();
        verify(eventPublisher).publishEvent(new HelperConfirmedEvent(8L, REQUEST_ID, NOON, NOON.plusHours(1)));
    }

    @Test
    void promoteOrReopen_예비없음_모집재개() {
        // given
        given(applicationRepository.findWaitingForUpdate(REQUEST_ID)).willReturn(List.of());

        // when
        ApplicationAfterAction result = waitingPromoter.promoteOrReopen(request, NOON.minusMinutes(40));

        // then
        assertThat(result).isEqualTo(ApplicationAfterAction.REOPENED);
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.RECRUITING);
    }
}
