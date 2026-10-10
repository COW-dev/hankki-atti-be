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

    List<HelpRequest> findByStudentAccountId(Long studentId);

    /**
     * 장애학생의 진행 중인 신청(모집 중·매칭 완료) 가운데 [start, end)와 구간이 겹치는 것이 있는지.
     * 끝 시각과 시작 시각이 같으면 겹치지 않는다 (예: 12:00~13:00과 13:00~14:00).
     */
    default boolean existsOverlapping(Long studentId, LocalDateTime start, LocalDateTime end) {
        return existsOverlappingWithStatus(studentId, start, end, HelpRequestStatus.IN_PROGRESS);
    }

    @Query("""
            select case when count(r) > 0 then true else false end from HelpRequest r
            where r.student.accountId = :studentId
              and r.status in :statuses
              and r.startAt < :end and :start < r.endAt""")
    boolean existsOverlappingWithStatus(@Param("studentId") Long studentId,
                                        @Param("start") LocalDateTime start,
                                        @Param("end") LocalDateTime end,
                                        @Param("statuses") List<HelpRequestStatus> statuses);

    /**
     * 도우미가 지원할 수 있는 신청: 모집 중·매칭 완료이고 아직 시작 전이며 시작 시각이 [from, toExclusive)인 것. 시작 시각 순.
     * 시작이 지난 모집 중 신청은 스케줄러가 곧 매칭 실패로 바꾸므로 넣지 않는다.
     */
    default List<HelpRequest> findOpen(LocalDateTime from, LocalDateTime toExclusive, LocalDateTime now) {
        return findOpenWithStatus(from, toExclusive, now, HelpRequestStatus.IN_PROGRESS);
    }

    @Query("""
            select r from HelpRequest r
            where r.status in :statuses
              and r.startAt > :now
              and r.startAt >= :from and r.startAt < :toExclusive
            order by r.startAt, r.id""")
    List<HelpRequest> findOpenWithStatus(@Param("from") LocalDateTime from,
                                         @Param("toExclusive") LocalDateTime toExclusive,
                                         @Param("now") LocalDateTime now,
                                         @Param("statuses") List<HelpRequestStatus> statuses);

    /**
     * 식사가 시작됐는데 아직 처리할 게 남은 신청: 모집 중이거나 예비·승격 응답 대기 지원이 남아 있는 신청.
     */
    default List<Long> findIdsToStart(LocalDateTime now, int limit) {
        return findStartedIdsWithPendingWork(now, HelpRequestStatus.RECRUITING,
                List.of(ApplicationStatus.WAITING, ApplicationStatus.PROMOTION_PENDING), PageRequest.of(0, limit));
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
                or exists (select a.id from Application a where a.helpRequest = r and a.status in :pending))
            order by r.startAt, r.id""")
    List<Long> findStartedIdsWithPendingWork(@Param("now") LocalDateTime now,
                                             @Param("recruiting") HelpRequestStatus recruiting,
                                             @Param("pending") List<ApplicationStatus> pending,
                                             Pageable pageable);

    @Query("""
            select r.id from HelpRequest r
            where r.status = :status and r.endAt <= :now
            order by r.endAt, r.id""")
    List<Long> findEndedIdsWithStatus(@Param("now") LocalDateTime now,
                                      @Param("status") HelpRequestStatus status,
                                      Pageable pageable);
}
