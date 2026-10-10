package com.hankkiatti.domain.helprequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplyBlockReason;
import com.hankkiatti.domain.application.entity.ApplyOutcome;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.service.ApplyPolicy;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.dto.response.OpenHelpRequestDateResponseDto;
import com.hankkiatti.domain.helprequest.dto.response.OpenHelpRequestResponseDto;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestErrorType;
import com.hankkiatti.domain.helprequest.exception.HelpRequestException;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.support.TestAccounts;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OpenHelpRequestServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-10-12(월) 09:00
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDateTime NOW = MONDAY.atTime(9, 0);
    private static final LocalDateTime NOON = MONDAY.atTime(12, 0);
    private static final Long HELPER_ID = 7L;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private HelperRepository helperRepository;

    private OpenHelpRequestService openHelpRequestService;

    private final Student student = TestProfiles.student(TestAccounts.withId(1L, AccountRole.STUDENT, "hash", false));
    private final Helper me = TestProfiles.helper(TestAccounts.withId(HELPER_ID, AccountRole.HELPER, "hash", false),
            "60230001");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        openHelpRequestService = new OpenHelpRequestService(helpRequestRepository, applicationRepository,
                helperRepository, new ApplyPolicy(), clock);
    }

    private void givenHelper() {
        given(helperRepository.existsById(HELPER_ID)).willReturn(true);
    }

    // 기본 기간(오늘 ~ 7일 뒤)으로 조회했을 때 돌아올 신청
    private void givenOpenRequests(HelpRequest... requests) {
        given(helpRequestRepository.findOpen(MONDAY.atStartOfDay(), MONDAY.plusDays(8).atStartOfDay(), NOW))
                .willReturn(List.of(requests));
    }

    private void givenMyActive(Application... applications) {
        given(applicationRepository.findActiveWithHelpRequestByHelperId(HELPER_ID)).willReturn(List.of(applications));
    }

    private HelpRequest recruiting(Long id, LocalDateTime startAt) {
        HelpRequest request = new HelpRequest(student, startAt, Set.of(HelpType.SEATING, HelpType.SERVING),
                null, "출입구");
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }

    private HelpRequest matched(Long id, LocalDateTime startAt) {
        HelpRequest request = recruiting(id, startAt);
        request.match(NOW.minusHours(1));
        return request;
    }

    private Application waitingOn(HelpRequest request) {
        return new Application(request, me, NOW.minusHours(1));
    }

    private Application matchedOn(HelpRequest request) {
        Application application = waitingOn(request);
        application.match(NOW.minusHours(1));
        return application;
    }

    private Application promotionPendingOn(HelpRequest request) {
        Application application = waitingOn(request);
        application.promote(NOW.minusHours(1), NOW.minusHours(1).plusMinutes(30), null, 1);
        return application;
    }

    private List<OpenHelpRequestResponseDto> cards(List<OpenHelpRequestDateResponseDto> result) {
        return result.stream().flatMap(date -> date.requests().stream()).toList();
    }

    private OpenHelpRequestResponseDto onlyCard() {
        List<OpenHelpRequestResponseDto> cards = cards(openHelpRequestService.getOpenRequests(HELPER_ID, null, null));
        assertThat(cards).hasSize(1);
        return cards.get(0);
    }

    @Test
    void getOpenRequests_모집중_MATCH이고블라인드필드만() {
        // given
        givenHelper();
        givenOpenRequests(recruiting(10L, NOON));
        givenMyActive();

        // when
        OpenHelpRequestResponseDto card = onlyCard();

        // then
        assertThat(card.id()).isEqualTo(10L);
        assertThat(card.startAt()).isEqualTo(NOON);
        assertThat(card.endAt()).isEqualTo(NOON.plusHours(1));
        assertThat(card.helpTypes()).containsExactly(HelpType.SERVING, HelpType.SEATING);
        assertThat(card.applyOutcome()).isEqualTo(ApplyOutcome.MATCH);
        assertThat(card.blockReason()).isNull();
    }

    @Test
    void getOpenRequests_매칭완료_WAITING() {
        // given
        givenHelper();
        givenOpenRequests(matched(10L, NOON));
        givenMyActive();

        // when & then
        assertThat(onlyCard().applyOutcome()).isEqualTo(ApplyOutcome.WAITING);
    }

    @Test
    void getOpenRequests_이미예비로지원한신청_ALREADY_APPLIED() {
        // given
        givenHelper();
        HelpRequest request = matched(10L, NOON);
        givenOpenRequests(request);
        givenMyActive(waitingOn(request));

        // when
        OpenHelpRequestResponseDto card = onlyCard();

        // then
        assertThat(card.applyOutcome()).isEqualTo(ApplyOutcome.BLOCKED);
        assertThat(card.blockReason()).isEqualTo(ApplyBlockReason.ALREADY_APPLIED);
    }

    @Test
    void getOpenRequests_이미지원했고내매칭과도겹침_ALREADY_APPLIED가먼저() {
        // given
        givenHelper();
        HelpRequest request = recruiting(10L, NOON);
        givenOpenRequests(request);
        givenMyActive(matchedOn(matched(20L, NOON.plusMinutes(30))), waitingOn(request));

        // when & then
        assertThat(onlyCard().blockReason()).isEqualTo(ApplyBlockReason.ALREADY_APPLIED);
    }

    @Test
    void getOpenRequests_내매칭완료와30분겹침_TIME_OVERLAP() {
        // given
        givenHelper();
        givenOpenRequests(recruiting(10L, NOON.plusMinutes(30)));
        givenMyActive(matchedOn(matched(20L, NOON)));

        // when
        OpenHelpRequestResponseDto card = onlyCard();

        // then
        assertThat(card.applyOutcome()).isEqualTo(ApplyOutcome.BLOCKED);
        assertThat(card.blockReason()).isEqualTo(ApplyBlockReason.TIME_OVERLAP);
    }

    @Test
    void getOpenRequests_내승격응답대기와겹침_TIME_OVERLAP() {
        // given — 수락하면 두 건이 겹치므로 확정 매칭처럼 막는다
        givenHelper();
        givenOpenRequests(recruiting(10L, NOON));
        givenMyActive(promotionPendingOn(matched(20L, NOON)));

        // when & then
        assertThat(onlyCard().blockReason()).isEqualTo(ApplyBlockReason.TIME_OVERLAP);
    }

    @Test
    void getOpenRequests_내예비와겹침_막지않음() {
        // given — 예비끼리는 여러 건 지원할 수 있다
        givenHelper();
        givenOpenRequests(recruiting(10L, NOON));
        givenMyActive(waitingOn(matched(20L, NOON)));

        // when & then
        assertThat(onlyCard().applyOutcome()).isEqualTo(ApplyOutcome.MATCH);
    }

    @Test
    void getOpenRequests_내매칭끝과시작이같음_겹치지않아MATCH() {
        // given — 12:00~13:00 매칭, 13:00 신청
        givenHelper();
        givenOpenRequests(recruiting(10L, NOON.plusHours(1)));
        givenMyActive(matchedOn(matched(20L, NOON)));

        // when & then
        assertThat(onlyCard().applyOutcome()).isEqualTo(ApplyOutcome.MATCH);
    }

    @Test
    void getOpenRequests_날짜별로묶고시작시각순유지() {
        // given
        givenHelper();
        LocalDateTime tuesdayNoon = MONDAY.plusDays(1).atTime(12, 0);
        givenOpenRequests(recruiting(10L, NOON), recruiting(11L, NOON.plusHours(5)), matched(12L, tuesdayNoon));
        givenMyActive();

        // when
        List<OpenHelpRequestDateResponseDto> result = openHelpRequestService.getOpenRequests(HELPER_ID, null, null);

        // then
        assertThat(result).extracting(OpenHelpRequestDateResponseDto::date).containsExactly(MONDAY, MONDAY.plusDays(1));
        assertThat(result.get(0).requests()).extracting(OpenHelpRequestResponseDto::id).containsExactly(10L, 11L);
        assertThat(result.get(1).requests()).extracting(OpenHelpRequestResponseDto::id).containsExactly(12L);
        assertThat(result.get(1).requests().get(0).applyOutcome()).isEqualTo(ApplyOutcome.WAITING);
    }

    @Test
    void getOpenRequests_from만있으면to는from더하기7일() {
        // given
        givenHelper();
        LocalDate from = MONDAY.plusDays(2);
        given(helpRequestRepository.findOpen(from.atStartOfDay(), from.plusDays(8).atStartOfDay(), NOW))
                .willReturn(List.of());
        givenMyActive();

        // when
        List<OpenHelpRequestDateResponseDto> result = openHelpRequestService.getOpenRequests(HELPER_ID, from, null);

        // then
        assertThat(result).isEmpty();
        verify(helpRequestRepository).findOpen(from.atStartOfDay(), from.plusDays(8).atStartOfDay(), NOW);
    }

    @Test
    void getOpenRequests_to가from보다앞_INVALID_DATE_RANGE() {
        // given
        givenHelper();

        // when & then
        assertThatThrownBy(() -> openHelpRequestService.getOpenRequests(HELPER_ID, MONDAY, MONDAY.minusDays(1)))
                .isInstanceOf(HelpRequestException.class)
                .extracting("errorCode").isEqualTo(HelpRequestErrorType.INVALID_DATE_RANGE);
        verify(helpRequestRepository, never()).findOpen(any(), any(), any());
    }

    @Test
    void getOpenRequests_31일초과_INVALID_DATE_RANGE이고30일은허용() {
        // given
        givenHelper();
        given(helpRequestRepository.findOpen(any(), any(), any())).willReturn(List.of());
        givenMyActive();

        // when & then
        assertThat(openHelpRequestService.getOpenRequests(HELPER_ID, MONDAY, MONDAY.plusDays(30))).isEmpty();
        assertThatThrownBy(() -> openHelpRequestService.getOpenRequests(HELPER_ID, MONDAY, MONDAY.plusDays(31)))
                .isInstanceOf(HelpRequestException.class)
                .extracting("errorCode").isEqualTo(HelpRequestErrorType.INVALID_DATE_RANGE);
    }

    @Test
    void getOpenRequests_장애학생계정_ACCESS_DENIED() {
        // given
        given(helperRepository.existsById(HELPER_ID)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> openHelpRequestService.getOpenRequests(HELPER_ID, null, null))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
        verify(helpRequestRepository, never()).findOpen(any(), any(), any());
    }
}
