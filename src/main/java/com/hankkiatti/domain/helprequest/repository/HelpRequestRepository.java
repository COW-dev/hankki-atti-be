package com.hankkiatti.domain.helprequest.repository;

import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, Long> {

    /**
     * 신청 행을 잠그고 가져온다. 같은 신청을 바꾸는 작업(지원·취소·자동 처리)은 이 락으로 순서를 맞춘다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from HelpRequest r where r.id = :id")
    Optional<HelpRequest> findByIdForUpdate(@Param("id") Long id);

    /**
     * 식사가 시작됐는데 아직 처리할 게 남은 신청: 모집 중이거나 예비 지원이 남아 있는 신청.
     */
    default List<Long> findIdsToStart(LocalDateTime now, int limit) {
        return findStartedIdsWithPendingWork(now, HelpRequestStatus.RECRUITING, ApplicationStatus.WAITING,
                PageRequest.of(0, limit));
    }

    /**
     * 식사가 끝났는데 아직 매칭 완료인 신청.
     */
    default List<Long> findIdsToComplete(LocalDateTime now, int limit) {
        return findEndedIdsWithStatus(now, HelpRequestStatus.MATCHED, PageRequest.of(0, limit));
    }

    @Query("""
            select r.id from HelpRequest r
            where r.startAt <= :now
              and (r.status = :recruiting
                or exists (select a.id from Application a where a.helpRequest = r and a.status = :waiting))
            order by r.startAt, r.id""")
    List<Long> findStartedIdsWithPendingWork(@Param("now") LocalDateTime now,
                                             @Param("recruiting") HelpRequestStatus recruiting,
                                             @Param("waiting") ApplicationStatus waiting,
                                             Pageable pageable);

    @Query("""
            select r.id from HelpRequest r
            where r.status = :status and r.endAt <= :now
            order by r.endAt, r.id""")
    List<Long> findEndedIdsWithStatus(@Param("now") LocalDateTime now,
                                      @Param("status") HelpRequestStatus status,
                                      Pageable pageable);
}
