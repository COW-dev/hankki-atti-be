package com.hankkiatti.domain.notification.service;

import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.notification.dto.response.NotificationReadAllResponseDto;
import com.hankkiatti.domain.notification.dto.response.NotificationResponseDto;
import com.hankkiatti.domain.notification.dto.response.NotificationUnreadCountResponseDto;
import com.hankkiatti.domain.notification.dto.response.NotificationsResponseDto;
import com.hankkiatti.domain.notification.entity.Notification;
import com.hankkiatti.domain.notification.entity.NotificationTargetType;
import com.hankkiatti.domain.notification.entity.NotificationType;
import com.hankkiatti.domain.notification.exception.NotificationErrorType;
import com.hankkiatti.domain.notification.exception.NotificationException;
import com.hankkiatti.domain.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인앱 알림 (요구사항 6장). 장애학생·도우미만 받는다. 알림을 만드는 쪽은 매칭·취소 이벤트를 받는 리스너다 (BE-51).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    /**
     * 알림을 하나 쌓는다. 문장은 부르는 쪽이 만든다 — 장애 유형·특이사항·메모를 넣지 않는다.
     */
    @Transactional
    public void notify(Long recipientId, NotificationType type, String message,
                       NotificationTargetType targetType, Long targetId) {
        Notification saved = notificationRepository.save(new Notification(
                accountRepository.getReferenceById(recipientId), type, message, targetType, targetId));
        // 문장은 로그에 남기지 않는다
        log.info("알림 저장: notificationId={}, recipientId={}, type={}", saved.getId(), recipientId, type);
    }

    /**
     * 내 알림 최신순. cursor는 앞 페이지의 nextCursor. 하나 더 읽어 다음 페이지가 있는지 판단한다.
     */
    @Transactional(readOnly = true)
    public NotificationsResponseDto getMyNotifications(Long accountId, Long cursor, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new NotificationException(NotificationErrorType.INVALID_PAGE_SIZE, "size=" + size);
        }
        List<Notification> found = notificationRepository.findPage(accountId, cursor, size + 1);
        boolean hasNext = found.size() > size;
        List<Notification> page = hasNext ? found.subList(0, size) : found;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new NotificationsResponseDto(page.stream().map(NotificationService::toResponse).toList(), nextCursor);
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponseDto countUnread(Long accountId) {
        return new NotificationUnreadCountResponseDto(notificationRepository.countUnread(accountId));
    }

    @Transactional
    public NotificationResponseDto markRead(Long accountId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, accountId)
                .orElseThrow(() -> new NotificationException(NotificationErrorType.NOT_FOUND,
                        "notificationId=" + notificationId + ", accountId=" + accountId));
        notification.markRead(LocalDateTime.now(clock));
        return toResponse(notification);
    }

    @Transactional
    public NotificationReadAllResponseDto markAllRead(Long accountId) {
        return new NotificationReadAllResponseDto(
                notificationRepository.markAllRead(accountId, LocalDateTime.now(clock)));
    }

    private static NotificationResponseDto toResponse(Notification notification) {
        return new NotificationResponseDto(notification.getId(), notification.getType(), notification.getMessage(),
                notification.getTargetType(), notification.getTargetId(), notification.isRead(),
                notification.getCreatedAt());
    }
}
