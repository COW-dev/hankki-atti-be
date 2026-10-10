package com.hankkiatti.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.application.dto.response.MyApplicationResponseDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationAfterAction;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.support.TestAccounts;
import com.hankkiatti.support.TestHelpRequests;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PromotionResponseServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-10-12(월) 12:00 식사, 11:20에 승격돼 마감 11:45
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 12, 12, 0);
    private static final LocalDateTime DEADLINE = NOON.minusMinutes(15);
    private static final Long HELPER_ID = 8L;
    private static final Long REQUEST_ID = 10L;
    private static final Long APPLICATION_ID = 32L;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private WaitingPromoter waitingPromoter;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private HelpRequest request;
    private Application pending;

    @BeforeEach
    void setUp() {
        request = TestHelpRequests.request(TestHelpRequests.student("60231234"), NOON);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        request.match(NOON.minusDays(1));
        Helper helper = TestProfiles.helper(TestAccounts.withId(HELPER_ID, AccountRole.HELPER, "hash", false),
                "60230002");
        ReflectionTestUtils.setField(helper, "accountId", HELPER_ID);
        pending = new Application(request, helper, NOON.minusDays(1));
        ReflectionTestUtils.setField(pending, "id", APPLICATION_ID);
        pending.promote(NOON.minusMinutes(40), DEADLINE);
    }

    private PromotionResponseService serviceAt(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL);
        return new PromotionResponseService(applicationRepository, helpRequestRepository, waitingPromoter,
                eventPublisher, clock);
    }

    private void givenMyApplicationLocked() {
        given(applicationRepository.findHelpRequestIdByIdAndHelperId(APPLICATION_ID, HELPER_ID))
                .willReturn(Optional.of(REQUEST_ID));
        given(helpRequestRepository.findByIdForUpdate(REQUEST_ID)).willReturn(Optional.of(request));
        given(applicationRepository.findByIdForUpdate(APPLICATION_ID)).willReturn(Optional.of(pending));
    }

    private void givenLockedForJob() {
        given(applicationRepository.findHelpRequestIdById(APPLICATION_ID)).willReturn(Optional.of(REQUEST_ID));
        given(helpRequestRepository.findByIdForUpdate(REQUEST_ID)).willReturn(Optional.of(request));
        given(applicationRepository.findByIdForUpdate(APPLICATION_ID)).willReturn(Optional.of(pending));
    }

    private static void assertError(Runnable action, ApplicationErrorType expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    @Test
    void accept_마감전_매칭완료하고장애학생정보와겹침제외이벤트() {
        // given
        givenMyApplicationLocked();

        // when
        MyApplicationResponseDto result = serviceAt(DEADLINE.minusMinutes(1)).accept(HELPER_ID, APPLICATION_ID);

        // then
        assertThat(pending.getStatus()).isEqualTo(ApplicationStatus.MATCHED);
        assertThat(result.status()).isEqualTo(ApplicationStatus.MATCHED);
        assertThat(result.student().name()).isEqualTo("학생60231234");
        assertThat(result.promotionDeadline()).isNull();
        verify(eventPublisher).publishEvent(new HelperConfirmedEvent(HELPER_ID, REQUEST_ID, NOON, NOON.plusHours(1)));
    }

    @Test
    void accept_마감지남_PROMOTION_EXPIRED() {
        // given
        givenMyApplicationLocked();

        // when & then
        assertError(() -> serviceAt(DEADLINE).accept(HELPER_ID, APPLICATION_ID),
                ApplicationErrorType.PROMOTION_EXPIRED);
        assertThat(pending.getStatus()).isEqualTo(ApplicationStatus.PROMOTION_PENDING);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void accept_응답대기아님_INVALID_STATUS() {
        // given
        givenMyApplicationLocked();
        pending.acceptPromotion(DEADLINE.minusMinutes(5));

        // when & then
        assertError(() -> serviceAt(DEADLINE.minusMinutes(1)).accept(HELPER_ID, APPLICATION_ID),
                ApplicationErrorType.INVALID_STATUS);
    }

    @Test
    void accept_남의지원_NOT_FOUND() {
        // given
        given(applicationRepository.findHelpRequestIdByIdAndHelperId(APPLICATION_ID, 99L))
                .willReturn(Optional.empty());

        // when & then
        assertError(() -> serviceAt(DEADLINE.minusMinutes(1)).accept(99L, APPLICATION_ID),
                ApplicationErrorType.NOT_FOUND);
        verifyNoInteractions(helpRequestRepository);
    }

    @Test
    void decline_마감지났어도식사전_승격거절하고다음예비로() {
        // given
        givenMyApplicationLocked();
        given(waitingPromoter.promoteOrReopen(request, DEADLINE.plusMinutes(5)))
                .willReturn(ApplicationAfterAction.PROMOTED);

        // when
        MyApplicationResponseDto result = serviceAt(DEADLINE.plusMinutes(5)).decline(HELPER_ID, APPLICATION_ID);

        // then
        assertThat(pending.getStatus()).isEqualTo(ApplicationStatus.PROMOTION_DECLINED);
        assertThat(result.status()).isEqualTo(ApplicationStatus.PROMOTION_DECLINED);
        assertThat(result.student()).isNull();
        verify(waitingPromoter).promoteOrReopen(request, DEADLINE.plusMinutes(5));
    }

    @Test
    void decline_식사시작뒤_PROMOTION_EXPIRED() {
        // given
        givenMyApplicationLocked();

        // when & then
        assertError(() -> serviceAt(NOON).decline(HELPER_ID, APPLICATION_ID), ApplicationErrorType.PROMOTION_EXPIRED);
        verifyNoInteractions(waitingPromoter);
    }

    @Test
    void expireUnanswered_마감지남_자동거절하고다음예비로() {
        // given
        givenLockedForJob();

        // when
        serviceAt(DEADLINE).expireUnanswered(APPLICATION_ID, DEADLINE);

        // then
        assertThat(pending.getStatus()).isEqualTo(ApplicationStatus.PROMOTION_DECLINED);
        verify(waitingPromoter).promoteOrReopen(request, DEADLINE);
    }

    @Test
    void expireUnanswered_그사이수락함_건너뜀() {
        // given
        givenLockedForJob();
        pending.acceptPromotion(DEADLINE.minusMinutes(1));

        // when
        serviceAt(DEADLINE).expireUnanswered(APPLICATION_ID, DEADLINE);

        // then
        assertThat(pending.getStatus()).isEqualTo(ApplicationStatus.MATCHED);
        verifyNoInteractions(waitingPromoter);
    }

    @Test
    void expireUnanswered_아직마감전_건너뜀() {
        // given
        givenLockedForJob();

        // when
        serviceAt(DEADLINE).expireUnanswered(APPLICATION_ID, DEADLINE.minusSeconds(1));

        // then
        assertThat(pending.getStatus()).isEqualTo(ApplicationStatus.PROMOTION_PENDING);
        verifyNoInteractions(waitingPromoter);
    }

    @Test
    void expireUnanswered_식사시작뒤_식사시작처리에맡김() {
        // given
        givenLockedForJob();

        // when
        serviceAt(NOON).expireUnanswered(APPLICATION_ID, NOON);

        // then
        assertThat(pending.getStatus()).isEqualTo(ApplicationStatus.PROMOTION_PENDING);
        verifyNoInteractions(waitingPromoter);
    }

    @Test
    void expireUnanswered_지원없음_아무것도하지않음() {
        // given
        given(applicationRepository.findHelpRequestIdById(APPLICATION_ID)).willReturn(Optional.empty());

        // when
        serviceAt(DEADLINE).expireUnanswered(APPLICATION_ID, DEADLINE);

        // then
        verify(helpRequestRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(waitingPromoter);
    }
}
