package com.hankkiatti.domain.helprequest.scheduler;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;

import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.helprequest.service.MealTimeService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

@ExtendWith(MockitoExtension.class)
class MealTimeJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 0);

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private MealTimeService mealTimeService;

    @InjectMocks
    private MealTimeJob mealTimeJob;

    @Test
    void processDue_시작처리후종료처리_한건이실패해도나머지계속() {
        // given
        given(helpRequestRepository.findIdsToStart(NOW, 200)).willReturn(List.of(1L, 2L));
        given(helpRequestRepository.findIdsToComplete(NOW, 200)).willReturn(List.of(3L));
        willThrow(new CannotAcquireLockException("lock wait timeout")).given(mealTimeService).startMeal(1L, NOW);

        // when
        mealTimeJob.processDue(NOW);

        // then
        InOrder order = inOrder(mealTimeService);
        order.verify(mealTimeService).startMeal(1L, NOW);
        order.verify(mealTimeService).startMeal(2L, NOW);
        order.verify(mealTimeService).completeMeal(3L, NOW);
    }
}
