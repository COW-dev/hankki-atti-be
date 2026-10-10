package com.hankkiatti.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.event.WaitingExcludedEvent;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OverlappingWaitExclusionServiceTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 12, 12, 0);
    private static final Long HELPER_ID = 7L;
    private static final Long WAITING_ID = 41L;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OverlappingWaitExclusionService exclusionService;

    private final Student student = TestHelpRequests.student("60231234");
    private final Helper helper = TestProfiles.helper(TestAccounts.withId(HELPER_ID, AccountRole.HELPER, "hash", false),
            "60230001");
    private HelpRequest waitingOn;
    private Application waiting;

    @BeforeEach
    void setUp() {
        exclusionService = new OverlappingWaitExclusionService(applicationRepository, helpRequestRepository,
                new ApplyPolicy(), eventPublisher);
        waitingOn = request(10L, NOON.plusMinutes(30));
        waiting = new Application(waitingOn, helper, NOON.minusDays(1));
        ReflectionTestUtils.setField(waiting, "id", WAITING_ID);
    }

    private HelpRequest request(Long id, LocalDateTime startAt) {
        HelpRequest created = TestHelpRequests.request(student, startAt);
        ReflectionTestUtils.setField(created, "id", id);
        return created;
    }

    private Application matchedAt(LocalDateTime startAt) {
        Application matched = new Application(request(20L, startAt), helper, NOON.minusDays(1));
        matched.match(NOON.minusDays(1));
        return matched;
    }

    private void givenLocked() {
        given(applicationRepository.findHelpRequestIdByIdAndHelperId(WAITING_ID, HELPER_ID)).willReturn(Optional.of(10L));
        given(helpRequestRepository.findByIdForUpdate(10L)).willReturn(Optional.of(waitingOn));
        given(applicationRepository.findByIdForUpdate(WAITING_ID)).willReturn(Optional.of(waiting));
    }

    @Test
    void excludeOne_겹치는확정매칭이있음_예비를자동제외() {
        // given — 12:00 매칭, 12:30 예비
        givenLocked();
        given(applicationRepository.findActiveWithHelpRequestByHelperId(HELPER_ID))
                .willReturn(List.of(matchedAt(NOON), waiting));

        // when & then
        assertThat(exclusionService.excludeOne(WAITING_ID, HELPER_ID)).isTrue();
        assertThat(waiting.getStatus()).isEqualTo(ApplicationStatus.EXCLUDED);
        verify(eventPublisher).publishEvent(new WaitingExcludedEvent(WAITING_ID));
    }

    @Test
    void excludeOne_그사이매칭을취소해겹치는확정이없음_예비유지() {
        // given
        givenLocked();
        given(applicationRepository.findActiveWithHelpRequestByHelperId(HELPER_ID)).willReturn(List.of(waiting));

        // when & then
        assertThat(exclusionService.excludeOne(WAITING_ID, HELPER_ID)).isFalse();
        assertThat(waiting.getStatus()).isEqualTo(ApplicationStatus.WAITING);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void excludeOne_이미예비가아님_그대로() {
        // given — 그사이 빠졌다
        waiting.withdraw(NOON.minusHours(3));
        givenLocked();

        // when & then
        assertThat(exclusionService.excludeOne(WAITING_ID, HELPER_ID)).isFalse();
        assertThat(waiting.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
    }

    @Test
    void excludeOne_지원이없어짐_그대로() {
        // given
        given(applicationRepository.findHelpRequestIdByIdAndHelperId(WAITING_ID, HELPER_ID)).willReturn(Optional.empty());

        // when & then
        assertThat(exclusionService.excludeOne(WAITING_ID, HELPER_ID)).isFalse();
    }
}
