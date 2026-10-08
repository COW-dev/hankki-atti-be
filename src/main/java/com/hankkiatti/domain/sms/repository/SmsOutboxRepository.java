package com.hankkiatti.domain.sms.repository;

import com.hankkiatti.domain.sms.entity.SmsOutbox;
import com.hankkiatti.domain.sms.entity.SmsOutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SmsOutboxRepository extends JpaRepository<SmsOutbox, Long> {

    default List<Long> findDispatchableIds(LocalDateTime now, LocalDateTime staleBefore, int limit) {
        return findDispatchableIds(now, staleBefore, SmsOutboxStatus.PENDING, SmsOutboxStatus.SENDING,
                PageRequest.of(0, limit));
    }

    default boolean claim(Long id, LocalDateTime now, LocalDateTime staleBefore) {
        return claim(id, now, staleBefore, SmsOutboxStatus.PENDING, SmsOutboxStatus.SENDING) == 1;
    }

    /**
     * 지금 보낼 수 있는 문자 ID. 발송 시각이 된 대기 문자와, 선점된 지 오래된(발송 중 서버가 죽은) 문자.
     */
    @Query("""
            select s.id from SmsOutbox s
            where (s.status = :pending and s.nextAttemptAt <= :now)
               or (s.status = :sending and s.claimedAt < :staleBefore)
            order by s.id""")
    List<Long> findDispatchableIds(@Param("now") LocalDateTime now,
                                   @Param("staleBefore") LocalDateTime staleBefore,
                                   @Param("pending") SmsOutboxStatus pending,
                                   @Param("sending") SmsOutboxStatus sending,
                                   Pageable pageable);

    /**
     * 문자를 선점한다. 조건부 UPDATE라 여러 스레드·서버가 동시에 시도해도 한 곳만 1을 받는다.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SmsOutbox s set s.status = :sending, s.claimedAt = :now
            where s.id = :id
              and ((s.status = :pending and s.nextAttemptAt <= :now)
                or (s.status = :sending and s.claimedAt < :staleBefore))""")
    int claim(@Param("id") Long id,
              @Param("now") LocalDateTime now,
              @Param("staleBefore") LocalDateTime staleBefore,
              @Param("pending") SmsOutboxStatus pending,
              @Param("sending") SmsOutboxStatus sending);
}
