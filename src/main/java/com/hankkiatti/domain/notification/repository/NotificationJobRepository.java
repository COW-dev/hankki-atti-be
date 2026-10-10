package com.hankkiatti.domain.notification.repository;

import com.hankkiatti.domain.notification.entity.NotificationJob;
import com.hankkiatti.domain.notification.entity.NotificationJobStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationJobRepository extends JpaRepository<NotificationJob, Long> {

    default List<Long> findProcessableIds(LocalDateTime now, LocalDateTime staleBefore, int limit) {
        return findProcessableIds(now, staleBefore, NotificationJobStatus.PENDING, NotificationJobStatus.PROCESSING,
                PageRequest.of(0, limit));
    }

    default boolean claim(Long id, LocalDateTime now, LocalDateTime staleBefore) {
        return claim(id, now, staleBefore, NotificationJobStatus.PENDING, NotificationJobStatus.PROCESSING) == 1;
    }

    /**
     * 지금 처리할 수 있는 작업 ID. 처리 시각이 된 대기 작업과, 선점된 지 오래된(처리 중 서버가 죽은) 작업.
     */
    @Query("""
            select j.id from NotificationJob j
            where (j.status = :pending and j.nextAttemptAt <= :now)
               or (j.status = :processing and j.claimedAt < :staleBefore)
            order by j.id""")
    List<Long> findProcessableIds(@Param("now") LocalDateTime now,
                                  @Param("staleBefore") LocalDateTime staleBefore,
                                  @Param("pending") NotificationJobStatus pending,
                                  @Param("processing") NotificationJobStatus processing,
                                  Pageable pageable);

    /**
     * 작업을 선점한다. 조건부 UPDATE라 여러 스레드·서버가 동시에 시도해도 한 곳만 1을 받는다.
     * 업무 커밋 직후(afterCommit) 같은 스레드에서도 불리므로 새 트랜잭션에서 한다 — 그 시점의 일반 트랜잭션은
     * 이미 끝난 업무 트랜잭션에 참여해 선점이 커밋되지 않고 행 잠금만 남는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update NotificationJob j set j.status = :processing, j.claimedAt = :now
            where j.id = :id
              and ((j.status = :pending and j.nextAttemptAt <= :now)
                or (j.status = :processing and j.claimedAt < :staleBefore))""")
    int claim(@Param("id") Long id,
              @Param("now") LocalDateTime now,
              @Param("staleBefore") LocalDateTime staleBefore,
              @Param("pending") NotificationJobStatus pending,
              @Param("processing") NotificationJobStatus processing);
}
