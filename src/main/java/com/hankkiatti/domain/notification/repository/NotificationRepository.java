package com.hankkiatti.domain.notification.repository;

import com.hankkiatti.domain.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 내 알림을 최신순으로 limit개. cursor가 있으면 그 알림보다 오래된 것부터 (커서 = 앞 페이지 마지막 알림 ID).
     * ID가 커질수록 최신이라 ID로 자른다 — 그사이 새 알림이 들어와도 다음 페이지가 밀리지 않는다.
     */
    default List<Notification> findPage(Long recipientId, Long cursor, int limit) {
        PageRequest page = PageRequest.of(0, limit);
        return cursor == null
                ? findLatest(recipientId, page)
                : findOlderThan(recipientId, cursor, page);
    }

    @Query("select n from Notification n where n.recipient.id = :recipientId order by n.id desc")
    List<Notification> findLatest(@Param("recipientId") Long recipientId, Pageable pageable);

    @Query("""
            select n from Notification n
            where n.recipient.id = :recipientId and n.id < :cursor
            order by n.id desc""")
    List<Notification> findOlderThan(@Param("recipientId") Long recipientId,
                                     @Param("cursor") Long cursor,
                                     Pageable pageable);

    @Query("select n from Notification n where n.id = :id and n.recipient.id = :recipientId")
    Optional<Notification> findByIdAndRecipientId(@Param("id") Long id, @Param("recipientId") Long recipientId);

    @Query("select count(n) from Notification n where n.recipient.id = :recipientId and n.readAt is null")
    long countUnread(@Param("recipientId") Long recipientId);

    /**
     * 안 읽은 내 알림을 모두 읽음으로. 조건부 UPDATE라 동시에 불려도 한 번만 바뀐다. 바뀐 개수를 돌려준다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n set n.readAt = :now, n.updatedAt = :now
            where n.recipient.id = :recipientId and n.readAt is null""")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") LocalDateTime now);
}
