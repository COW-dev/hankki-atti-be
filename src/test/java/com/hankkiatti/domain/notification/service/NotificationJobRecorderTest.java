package com.hankkiatti.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.application.event.HelperConfirmedEvent;
import com.hankkiatti.domain.application.event.PromotionPendingEvent;
import com.hankkiatti.domain.application.event.WaitingRegisteredEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestCanceledByStudentEvent;
import com.hankkiatti.domain.helprequest.event.HelpRequestFailedEvent;
import com.hankkiatti.domain.notification.entity.NotificationJob;
import com.hankkiatti.domain.notification.entity.NotificationJobStatus;
import com.hankkiatti.domain.notification.entity.NotificationJobType;
import com.hankkiatti.domain.notification.repository.NotificationJobRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationJobRecorderTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 15, 0);
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 10, 12, 12, 0);

    @Mock
    private NotificationJobRepository notificationJobRepository;

    @Mock
    private NotificationJobRelay notificationJobRelay;

    private NotificationJobRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new NotificationJobRecorder(notificationJobRepository, notificationJobRelay,
                Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
        given(notificationJobRepository.save(any(NotificationJob.class))).willAnswer(invocation -> {
            NotificationJob saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
    }

    private List<NotificationJob> savedJobs(int count) {
        ArgumentCaptor<NotificationJob> saved = ArgumentCaptor.forClass(NotificationJob.class);
        verify(notificationJobRepository, times(count)).save(saved.capture());
        return saved.getAllValues();
    }

    @Test
    void 이벤트마다_종류대상값을담은대기작업() {
        // when
        recorder.onHelperConfirmed(new HelperConfirmedEvent(7L, 10L, 31L, NOON, NOON.plusHours(1),
                HelperConfirmedEvent.Kind.PROMOTED));
        recorder.onPromotionPending(new PromotionPendingEvent(31L, true));
        recorder.onWaitingRegistered(new WaitingRegisteredEvent(32L, 2));
        recorder.onRequestFailed(new HelpRequestFailedEvent(10L, 1L, NOON));

        // then
        assertThat(savedJobs(4))
                .extracting(NotificationJob::getType, NotificationJob::getTargetId, NotificationJob::getDetail)
                .containsExactly(
                        tuple(NotificationJobType.HELPER_CONFIRMED, 31L, "PROMOTED"),
                        tuple(NotificationJobType.PROMOTION_PENDING, 31L, "true"),
                        tuple(NotificationJobType.WAITING_REGISTERED, 32L, "2"),
                        tuple(NotificationJobType.REQUEST_FAILED, 10L, null));
    }

    @Test
    void 학생취소_지원마다작업하나() {
        // when
        recorder.onCanceledByStudent(new HelpRequestCanceledByStudentEvent(10L, NOON, List.of(31L, 32L, 33L)));

        // then
        List<NotificationJob> jobs = savedJobs(3);
        assertThat(jobs).extracting(NotificationJob::getTargetId).containsExactly(31L, 32L, 33L);
        assertThat(jobs).allSatisfy(job -> {
            assertThat(job.getType()).isEqualTo(NotificationJobType.STUDENT_CANCELED);
            assertThat(job.getStatus()).isEqualTo(NotificationJobStatus.PENDING);
            assertThat(job.getNextAttemptAt()).isEqualTo(NOW);
        });
    }
}
