package com.hankkiatti.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.application.dto.response.ApplyResponseDto;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import com.hankkiatti.domain.application.event.WaitingRegisteredEvent;
import com.hankkiatti.domain.application.exception.ApplicationErrorType;
import com.hankkiatti.domain.application.exception.ApplicationException;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.exception.HelpRequestErrorType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestException;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.support.TestHelpRequests;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-10-12(월) 09:00
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0);
    private static final LocalDateTime NOON = NOW.withHour(12);
    private static final Long HELPER_ID = 7L;
    private static final Long REQUEST_ID = 10L;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private HelperRepository helperRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ApplicationRepository applicationRepository;

    private ApplicationService applicationService;

    private final Student student = TestHelpRequests.student("60231234");
    private final Helper me = TestHelpRequests.helper("60230001");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        applicationService = new ApplicationService(helpRequestRepository, helperRepository, applicationRepository,
                new ApplyPolicy(), eventPublisher, clock);
        ReflectionTestUtils.setField(me, "accountId", HELPER_ID);
    }

    private HelpRequest request(Long id, LocalDateTime startAt) {
        HelpRequest request = TestHelpRequests.request(student, startAt);
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }

    private HelpRequest matchedRequest(Long id, LocalDateTime startAt) {
        HelpRequest request = request(id, startAt);
        request.match(NOW.minusHours(1));
        return request;
    }

    private Application matchedOn(HelpRequest request) {
        Application application = new Application(request, me, NOW.minusHours(1));
        application.match(NOW.minusHours(1));
        return application;
    }

    private void givenLocked(HelpRequest request) {
        given(helpRequestRepository.findByIdForUpdate(request.getId())).willReturn(Optional.of(request));
        given(helperRepository.findByIdForUpdate(HELPER_ID)).willReturn(Optional.of(me));
    }

    private void givenMyActive(Application... applications) {
        given(applicationRepository.findActiveWithHelpRequestByHelperId(HELPER_ID)).willReturn(List.of(applications));
    }

    private void givenSaveAssignsId() {
        given(applicationRepository.save(any(Application.class))).willAnswer(invocation -> {
            Application saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 31L);
            return saved;
        });
    }

    private void assertApplicationError(Runnable call, ApplicationErrorType expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApplicationException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    @Test
    void apply_모집중신청_바로매칭되고장애학생이름과카톡ID() {
        // given
        HelpRequest request = request(REQUEST_ID, NOON);
        givenLocked(request);
        givenMyActive();
        givenSaveAssignsId();

        // when
        ApplyResponseDto result = applicationService.apply(HELPER_ID, REQUEST_ID);

        // then
        assertThat(result.applicationId()).isEqualTo(31L);
        assertThat(result.status()).isEqualTo(ApplicationStatus.MATCHED);
        assertThat(result.waitingOrder()).isNull();
        assertThat(result.student().name()).isEqualTo(student.getName());
        assertThat(result.student().kakaoId()).isEqualTo(student.getKakaoId());
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.MATCHED);
        assertThat(request.getFirstMatchedAt()).isEqualTo(NOW);
        verify(eventPublisher).publishEvent(new HelperConfirmedEvent(HELPER_ID, REQUEST_ID, 31L, NOON, NOON.plusHours(1),
                HelperConfirmedEvent.Kind.DIRECT_MATCH));
    }

    @Test
    void apply_매칭완료신청_예비N번이고학생정보없음() {
        // given — 이미 예비 1명
        HelpRequest request = matchedRequest(REQUEST_ID, NOON);
        givenLocked(request);
        givenMyActive();
        given(applicationRepository.countByHelpRequestIdAndStatus(REQUEST_ID, ApplicationStatus.WAITING)).willReturn(1L);
        givenSaveAssignsId();

        // when
        ApplyResponseDto result = applicationService.apply(HELPER_ID, REQUEST_ID);

        // then
        assertThat(result.status()).isEqualTo(ApplicationStatus.WAITING);
        assertThat(result.waitingOrder()).isEqualTo(2);
        assertThat(result.student()).isNull();
        verify(eventPublisher).publishEvent(new WaitingRegisteredEvent(31L, 2));
    }

    @Test
    void apply_이미이신청에진행중지원_ALREADY_APPLIED() {
        // given
        HelpRequest request = matchedRequest(REQUEST_ID, NOON);
        givenLocked(request);
        givenMyActive(new Application(request, me, NOW.minusHours(1)));

        // when & then
        assertApplicationError(() -> applicationService.apply(HELPER_ID, REQUEST_ID),
                ApplicationErrorType.ALREADY_APPLIED);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void apply_내매칭과30분겹침_TIME_OVERLAP이고신청은그대로() {
        // given
        HelpRequest request = request(REQUEST_ID, NOON.plusMinutes(30));
        givenLocked(request);
        givenMyActive(matchedOn(matchedRequest(20L, NOON)));

        // when & then
        assertApplicationError(() -> applicationService.apply(HELPER_ID, REQUEST_ID),
                ApplicationErrorType.TIME_OVERLAP);
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.RECRUITING);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void apply_내예비와겹침_막지않고바로매칭() {
        // given
        HelpRequest request = request(REQUEST_ID, NOON);
        givenLocked(request);
        givenMyActive(new Application(matchedRequest(20L, NOON), me, NOW.minusHours(1)));
        givenSaveAssignsId();

        // when & then
        assertThat(applicationService.apply(HELPER_ID, REQUEST_ID).status()).isEqualTo(ApplicationStatus.MATCHED);
    }

    @Test
    void apply_끝난지원만있음_재지원받음() {
        // given — 예비에서 빠진 뒤 다시 지원
        HelpRequest request = matchedRequest(REQUEST_ID, NOON);
        givenLocked(request);
        givenMyActive();
        given(applicationRepository.countByHelpRequestIdAndStatus(REQUEST_ID, ApplicationStatus.WAITING)).willReturn(0L);
        givenSaveAssignsId();

        // when & then
        ApplyResponseDto result = applicationService.apply(HELPER_ID, REQUEST_ID);
        assertThat(result.status()).isEqualTo(ApplicationStatus.WAITING);
        assertThat(result.waitingOrder()).isEqualTo(1);
    }

    @Test
    void apply_시작시각이지났거나취소된신청_NOT_OPEN() {
        // given — 시작 = 지금
        HelpRequest started = request(REQUEST_ID, NOW);
        givenLocked(started);

        // when & then
        assertApplicationError(() -> applicationService.apply(HELPER_ID, REQUEST_ID), ApplicationErrorType.NOT_OPEN);

        // given — 철회된 신청
        HelpRequest withdrawn = request(11L, NOON);
        withdrawn.withdraw(NOW);
        given(helpRequestRepository.findByIdForUpdate(11L)).willReturn(Optional.of(withdrawn));

        // when & then
        assertApplicationError(() -> applicationService.apply(HELPER_ID, 11L), ApplicationErrorType.NOT_OPEN);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void apply_없는신청_HELP_REQUEST_NOT_FOUND() {
        // given
        given(helpRequestRepository.findByIdForUpdate(REQUEST_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applicationService.apply(HELPER_ID, REQUEST_ID))
                .isInstanceOf(HelpRequestException.class)
                .extracting("errorCode").isEqualTo(HelpRequestErrorType.NOT_FOUND);
    }

    @Test
    void apply_도우미가아님_ACCESS_DENIED() {
        // given
        given(helpRequestRepository.findByIdForUpdate(REQUEST_ID)).willReturn(Optional.of(request(REQUEST_ID, NOON)));
        given(helperRepository.findByIdForUpdate(HELPER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> applicationService.apply(HELPER_ID, REQUEST_ID))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
        verify(applicationRepository, never()).save(any());
    }
}
