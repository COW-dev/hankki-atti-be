package com.hankkiatti.domain.mail.repository;

import com.hankkiatti.domain.mail.entity.MailOutbox;
import com.hankkiatti.domain.mail.entity.MailOutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MailOutboxRepository extends JpaRepository<MailOutbox, Long> {

    default List<Long> findDispatchableIds(LocalDateTime now, LocalDateTime staleBefore, int limit) {
        return findDispatchableIds(now, staleBefore, MailOutboxStatus.PENDING, MailOutboxStatus.SENDING,
                PageRequest.of(0, limit));
    }

    default boolean claim(Long id, LocalDateTime now, LocalDateTime staleBefore) {
        return claim(id, now, staleBefore, MailOutboxStatus.PENDING, MailOutboxStatus.SENDING) == 1;
    }

    /**
     * 지금 보낼 수 있는 메일 ID. 발송 시각이 된 대기 메일과, 선점된 지 오래된(발송 중 서버가 죽은) 메일.
     */
    @Query("""
            select m.id from MailOutbox m
            where (m.status = :pending and m.nextAttemptAt <= :now)
               or (m.status = :sending and m.claimedAt < :staleBefore)
            order by m.id""")
    List<Long> findDispatchableIds(@Param("now") LocalDateTime now,
                                   @Param("staleBefore") LocalDateTime staleBefore,
                                   @Param("pending") MailOutboxStatus pending,
                                   @Param("sending") MailOutboxStatus sending,
                                   Pageable pageable);

    /**
     * 메일을 선점한다. 조건부 UPDATE라 여러 스레드·서버가 동시에 시도해도 한 곳만 1을 받는다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MailOutbox m set m.status = :sending, m.claimedAt = :now
            where m.id = :id
              and ((m.status = :pending and m.nextAttemptAt <= :now)
                or (m.status = :sending and m.claimedAt < :staleBefore))""")
    int claim(@Param("id") Long id,
              @Param("now") LocalDateTime now,
              @Param("staleBefore") LocalDateTime staleBefore,
              @Param("pending") MailOutboxStatus pending,
              @Param("sending") MailOutboxStatus sending);
}
