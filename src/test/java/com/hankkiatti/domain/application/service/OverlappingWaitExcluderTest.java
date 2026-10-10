package com.hankkiatti.domain.application.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

@ExtendWith(MockitoExtension.class)
class OverlappingWaitExcluderTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 12, 12, 0);

    @Mock
    private OverlappingWaitExclusionService exclusionService;

    @InjectMocks
    private OverlappingWaitExcluder excluder;

    @Test
    void onHelperConfirmed_대상마다한건씩처리하고_한건이실패해도나머지계속() {
        // given
        given(exclusionService.findTargets(7L, 10L, NOON, NOON.plusHours(1))).willReturn(List.of(41L, 42L, 43L));
        willThrow(new CannotAcquireLockException("lock timeout")).given(exclusionService).excludeOne(42L, 7L);

        // when
        excluder.onHelperConfirmed(new HelperConfirmedEvent(7L, 10L, 31L, NOON, NOON.plusHours(1),
                HelperConfirmedEvent.Kind.DIRECT_MATCH));

        // then
        verify(exclusionService).excludeOne(41L, 7L);
        verify(exclusionService).excludeOne(42L, 7L);
        verify(exclusionService).excludeOne(43L, 7L);
    }
}
