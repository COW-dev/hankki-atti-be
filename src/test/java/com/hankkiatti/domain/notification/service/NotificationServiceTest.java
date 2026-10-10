package com.hankkiatti.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.notification.dto.response.NotificationResponseDto;
import com.hankkiatti.domain.notification.dto.response.NotificationsResponseDto;
import com.hankkiatti.domain.notification.entity.Notification;
import com.hankkiatti.domain.notification.entity.NotificationTargetType;
import com.hankkiatti.domain.notification.entity.NotificationType;
import com.hankkiatti.domain.notification.exception.NotificationErrorType;
import com.hankkiatti.domain.notification.exception.NotificationException;
import com.hankkiatti.domain.notification.repository.NotificationRepository;
import com.hankkiatti.support.TestAccounts;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 15, 0);
    private static final Long ME = 7L;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AccountRepository accountRepository;

    private NotificationService notificationService;

    private final Account me = TestAccounts.withId(ME, AccountRole.HELPER, "hash", false);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        notificationService = new NotificationService(notificationRepository, accountRepository, clock);
    }

    private Notification notification(Long id) {
        Notification notification = new Notification(me, NotificationType.APPLICATION_MATCHED, "매칭됐어요",
                NotificationTargetType.APPLICATION, 31L);
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    // ID가 큰 것부터 (최신순)
    private List<Notification> notifications(long fromId, long toId) {
        return LongStream.iterate(fromId, id -> id >= toId, id -> id - 1).mapToObj(this::notification).toList();
    }

    @Test
    void notify_받는사람과종류문장대상으로저장() {
        // given
        given(accountRepository.getReferenceById(ME)).willReturn(me);
        given(notificationRepository.save(any(Notification.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        notificationService.notify(ME, NotificationType.STUDENT_CANCELED, "학생이 취소했어요",
                NotificationTargetType.APPLICATION, 31L);

        // then
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getRecipient()).isEqualTo(me);
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.STUDENT_CANCELED);
        assertThat(saved.getValue().getMessage()).isEqualTo("학생이 취소했어요");
        assertThat(saved.getValue().getTargetType()).isEqualTo(NotificationTargetType.APPLICATION);
        assertThat(saved.getValue().getTargetId()).isEqualTo(31L);
        assertThat(saved.getValue().isRead()).isFalse();
    }

    @Test
    void getMyNotifications_더있음_size개와마지막ID를다음커서로() {
        // given — size 3이면 4개를 읽어 본다
        given(notificationRepository.findPage(ME, null, 4)).willReturn(notifications(10, 7));

        // when
        NotificationsResponseDto result = notificationService.getMyNotifications(ME, null, 3);

        // then
        assertThat(result.items()).extracting(NotificationResponseDto::id).containsExactly(10L, 9L, 8L);
        assertThat(result.nextCursor()).isEqualTo(8L);
    }

    @Test
    void getMyNotifications_마지막페이지_다음커서없음() {
        // given
        given(notificationRepository.findPage(ME, 8L, 4)).willReturn(notifications(7, 6));

        // when
        NotificationsResponseDto result = notificationService.getMyNotifications(ME, 8L, 3);

        // then
        assertThat(result.items()).extracting(NotificationResponseDto::id).containsExactly(7L, 6L);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void getMyNotifications_size범위밖_INVALID_PAGE_SIZE() {
        // when & then
        for (int size : new int[] {0, 51}) {
            assertThatThrownBy(() -> notificationService.getMyNotifications(ME, null, size))
                    .isInstanceOf(NotificationException.class)
                    .extracting("errorCode").isEqualTo(NotificationErrorType.INVALID_PAGE_SIZE);
        }
        verify(notificationRepository, never()).findPage(any(), any(), anyInt());
    }

    @Test
    void markRead_내알림_읽음으로바꾸고이미읽었으면처음시각유지() {
        // given
        Notification notification = notification(5L);
        notification.markRead(NOW.minusHours(1));
        Notification unread = notification(6L);
        given(notificationRepository.findByIdAndRecipientId(5L, ME)).willReturn(Optional.of(notification));
        given(notificationRepository.findByIdAndRecipientId(6L, ME)).willReturn(Optional.of(unread));

        // when
        NotificationResponseDto already = notificationService.markRead(ME, 5L);
        NotificationResponseDto now = notificationService.markRead(ME, 6L);

        // then
        assertThat(already.read()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(NOW.minusHours(1));
        assertThat(now.read()).isTrue();
        assertThat(unread.getReadAt()).isEqualTo(NOW);
    }

    @Test
    void markRead_남의알림_NOT_FOUND() {
        // given
        given(notificationRepository.findByIdAndRecipientId(5L, ME)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> notificationService.markRead(ME, 5L))
                .isInstanceOf(NotificationException.class)
                .extracting("errorCode").isEqualTo(NotificationErrorType.NOT_FOUND);
    }

    @Test
    void markAllRead_바뀐개수반환() {
        // given
        given(notificationRepository.markAllRead(ME, NOW)).willReturn(3);

        // when & then
        assertThat(notificationService.markAllRead(ME).readCount()).isEqualTo(3);
    }
}
