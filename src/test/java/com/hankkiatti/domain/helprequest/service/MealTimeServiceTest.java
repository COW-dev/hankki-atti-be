package com.hankkiatti.domain.helprequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.event.HelpRequestFailedEvent;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.support.TestHelpRequests;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MealTimeServiceTest {

    private static final LocalDateTime LUNCH = LocalDateTime.of(2026, 10, 7, 12, 0);

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MealTimeService mealTimeService;

    private HelpRequest request(Long id) {
        Student student = TestHelpRequests.student("60231234");
        ReflectionTestUtils.setField(student, "accountId", 10L);
        HelpRequest request = TestHelpRequests.request(student, LUNCH);
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }

    private Application waiting(HelpRequest request) {
        return new Application(request, TestHelpRequests.helper("60230001"), LUNCH.minusHours(2));
    }

    private Application matched(HelpRequest request) {
        Application application = new Application(request, TestHelpRequests.helper("60230002"), LUNCH.minusHours(3));
        application.match(LUNCH.minusHours(3));
        return application;
    }

    @Test
    void startMeal_모집중인신청_매칭실패하고이벤트발행() {
        // given
        HelpRequest request = request(1L);
        given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(request));
        given(applicationRepository.findByHelpRequestIdAndStatus(1L, ApplicationStatus.WAITING)).willReturn(List.of());

        // when
        mealTimeService.startMeal(1L, LUNCH);

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.FAILED);
        verify(eventPublisher).publishEvent(new HelpRequestFailedEvent(1L, 10L, LUNCH));
    }

    @Test
    void startMeal_매칭완료신청의예비_예비종료하고매칭은유지() {
        // given
        HelpRequest request = request(1L);
        request.match(LUNCH.minusHours(3));
        Application waiting = waiting(request);
        given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(request));
        given(applicationRepository.findByHelpRequestIdAndStatus(1L, ApplicationStatus.WAITING))
                .willReturn(List.of(waiting));

        // when
        mealTimeService.startMeal(1L, LUNCH.plusMinutes(1));

        // then
        assertThat(waiting.getStatus()).isEqualTo(ApplicationStatus.EXPIRED);
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.MATCHED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void startMeal_잠근뒤보니아직시작전_건너뜀() {
        // given
        given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(request(1L)));

        // when
        mealTimeService.startMeal(1L, LUNCH.minusSeconds(1));

        // then
        verify(applicationRepository, never()).findByHelpRequestIdAndStatus(anyLong(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void startMeal_그사이학생이철회_실패처리하지않음() {
        // given
        HelpRequest request = request(1L);
        request.withdraw(LUNCH.minusMinutes(10));
        given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(request));
        given(applicationRepository.findByHelpRequestIdAndStatus(1L, ApplicationStatus.WAITING)).willReturn(List.of());

        // when
        mealTimeService.startMeal(1L, LUNCH);

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.CANCELED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void startMeal_신청없음_아무것도하지않음() {
        // given
        given(helpRequestRepository.findByIdForUpdate(9L)).willReturn(Optional.empty());

        // when
        mealTimeService.startMeal(9L, LUNCH);

        // then
        verify(applicationRepository, never()).findByHelpRequestIdAndStatus(anyLong(), any());
    }

    @Test
    void completeMeal_식사종료_이용완료하고봉사시간1시간() {
        // given
        HelpRequest request = request(1L);
        request.match(LUNCH.minusHours(3));
        Application matched = matched(request);
        given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(request));
        given(applicationRepository.findByHelpRequestIdAndStatus(1L, ApplicationStatus.MATCHED))
                .willReturn(List.of(matched));

        // when
        mealTimeService.completeMeal(1L, LUNCH.plusHours(1));

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.COMPLETED);
        assertThat(request.getCompletedAt()).isEqualTo(LUNCH.plusHours(1));
        assertThat(matched.getStatus()).isEqualTo(ApplicationStatus.COMPLETED);
        assertThat(matched.getVolunteerHours()).isEqualByComparingTo(new BigDecimal("1.0"));
    }

    @Test
    void completeMeal_아직식사중_건너뜀() {
        // given
        HelpRequest request = request(1L);
        request.match(LUNCH.minusHours(3));
        given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(request));

        // when
        mealTimeService.completeMeal(1L, LUNCH.plusMinutes(59));

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.MATCHED);
    }

    @Test
    void completeMeal_그사이학생이취소_건너뜀() {
        // given
        HelpRequest request = request(1L);
        request.match(LUNCH.minusHours(3));
        request.cancelByStudent(LUNCH.minusHours(1));
        given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(request));

        // when
        mealTimeService.completeMeal(1L, LUNCH.plusHours(1));

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.CANCELED);
        verify(applicationRepository, never()).findByHelpRequestIdAndStatus(anyLong(), any());
    }

    @Test
    void completeMeal_매칭된지원이없어도_신청은이용완료() {
        // given
        HelpRequest request = request(1L);
        request.match(LUNCH.minusHours(3));
        given(helpRequestRepository.findByIdForUpdate(1L)).willReturn(Optional.of(request));
        given(applicationRepository.findByHelpRequestIdAndStatus(1L, ApplicationStatus.MATCHED)).willReturn(List.of());

        // when
        mealTimeService.completeMeal(1L, LUNCH.plusHours(1));

        // then
        assertThat(request.getStatus()).isEqualTo(HelpRequestStatus.COMPLETED);
    }
}
