package com.hankkiatti.domain.application.repository;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByHelpRequestIdAndStatus(Long helpRequestId, ApplicationStatus status);

    // 예비 순번 계산용. 신청 행을 잠근 뒤 세므로 그 사이 다른 예비가 끼어들지 않는다
    long countByHelpRequestIdAndStatus(Long helpRequestId, ApplicationStatus status);

    /**
     * 내 지원의 신청 ID. 엔티티를 읽지 않고 값만 가져온다 — 신청 행을 잠그기 전에 지원 엔티티를 영속성 컨텍스트에 올리면
     * 잠근 뒤 다시 읽어도 처음 읽은(오래된) 상태가 그대로 쓰인다. 남의 지원이면 비어 있다.
     */
    @Query("select a.helpRequest.id from Application a where a.id = :id and a.helper.accountId = :helperId")
    Optional<Long> findHelpRequestIdByIdAndHelperId(@Param("id") Long id, @Param("helperId") Long helperId);

    /**
     * 지원의 신청 ID (승격 응답 마감 자동 처리). 위와 같은 이유로 엔티티가 아니라 값만 읽는다.
     */
    @Query("select a.helpRequest.id from Application a where a.id = :id")
    Optional<Long> findHelpRequestIdById(@Param("id") Long id);

    /**
     * 지원 행을 잠그고 가져온다. 신청 행 락 다음에 잡는다 (락 순서: 신청 → 지원 → 도우미).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :id")
    Optional<Application> findByIdForUpdate(@Param("id") Long id);

    /**
     * 도우미의 예비 중 [start, end)와 이용 시간이 겹치는 것의 ID. 방금 매칭된 신청은 뺀다 (겹치는 예비 자동 제외 대상).
     */
    default List<Long> findWaitingIdsOverlapping(Long helperId, LocalDateTime start, LocalDateTime end,
                                                 Long exceptHelpRequestId) {
        return findIdsByHelperAndStatusOverlapping(helperId, ApplicationStatus.WAITING, start, end,
                exceptHelpRequestId);
    }

    @Query("""
            select a.id from Application a
            where a.helper.accountId = :helperId and a.status = :status
              and a.helpRequest.id <> :exceptHelpRequestId
              and a.helpRequest.startAt < :end and :start < a.helpRequest.endAt
            order by a.helpRequest.startAt, a.id""")
    List<Long> findIdsByHelperAndStatusOverlapping(@Param("helperId") Long helperId,
                                                   @Param("status") ApplicationStatus status,
                                                   @Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end,
                                                   @Param("exceptHelpRequestId") Long exceptHelpRequestId);

    /**
     * 신청의 예비를 지원 순으로 잠그고 가져온다 (예비 승격 후보). 신청 행 락 다음에 잡는다.
     */
    default List<Application> findWaitingForUpdate(Long helpRequestId) {
        return findByHelpRequestIdAndStatusForUpdate(helpRequestId, ApplicationStatus.WAITING);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from Application a
            where a.helpRequest.id = :helpRequestId and a.status = :status
            order by a.appliedAt, a.id""")
    List<Application> findByHelpRequestIdAndStatusForUpdate(@Param("helpRequestId") Long helpRequestId,
                                                            @Param("status") ApplicationStatus status);

    /**
     * 신청의 진행 중 지원(매칭 완료·승격 응답 대기·예비)을 잠그고 가져온다 (장애학생 매칭 취소). 신청 행 락 다음에 잡는다.
     */
    default List<Application> findActiveForUpdate(Long helpRequestId) {
        return findByHelpRequestIdAndStatusInForUpdate(helpRequestId, ApplicationStatus.ACTIVE);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from Application a
            where a.helpRequest.id = :helpRequestId and a.status in :statuses
            order by a.id""")
    List<Application> findByHelpRequestIdAndStatusInForUpdate(@Param("helpRequestId") Long helpRequestId,
                                                              @Param("statuses") Collection<ApplicationStatus> statuses);

    /**
     * 신청들에 매칭된 지원(매칭 완료·이용 완료·노쇼)을 도우미와 함께 가져온다. 장애학생에게 도우미 이름·카톡 ID를 보여 줄 때 쓴다.
     * 승격 응답 대기 중인 지원은 넣지 않는다 — 확정되지 않은 도우미의 연락처를 미리 알리지 않으려고.
     */
    default List<Application> findMatchedWithHelper(Collection<Long> helpRequestIds) {
        return findWithHelperByHelpRequestIdInAndStatusIn(helpRequestIds,
                List.of(ApplicationStatus.MATCHED, ApplicationStatus.COMPLETED, ApplicationStatus.NO_SHOW));
    }

    @Query("""
            select a from Application a join fetch a.helper
            where a.helpRequest.id in :helpRequestIds and a.status in :statuses""")
    List<Application> findWithHelperByHelpRequestIdInAndStatusIn(
            @Param("helpRequestIds") Collection<Long> helpRequestIds,
            @Param("statuses") Collection<ApplicationStatus> statuses);

    /**
     * 도우미의 진행 중인 지원(매칭 완료·승격 응답 대기·예비)을 신청과 함께 가져온다.
     * 요청 목록에서 이미 지원한 신청과 확정 매칭과 겹치는 신청을 가려낼 때 쓴다.
     */
    default List<Application> findActiveWithHelpRequestByHelperId(Long helperId) {
        return findWithHelpRequestByHelperIdAndStatusIn(helperId, ApplicationStatus.ACTIVE);
    }

    @Query("""
            select a from Application a join fetch a.helpRequest
            where a.helper.accountId = :helperId and a.status in :statuses""")
    List<Application> findWithHelpRequestByHelperIdAndStatusIn(@Param("helperId") Long helperId,
                                                               @Param("statuses") Collection<ApplicationStatus> statuses);

    /**
     * 도우미의 지원 전부를 신청·장애학생과 함께 가져온다 (매칭 현황). 장애학생 정보는 매칭 완료 카드에만 쓴다.
     */
    @Query("""
            select a from Application a join fetch a.helpRequest r join fetch r.student
            where a.helper.accountId = :helperId""")
    List<Application> findMineWithHelpRequest(@Param("helperId") Long helperId);

    /**
     * 신청들의 예비를 지원 순으로 가져온다 (예비 순번 계산). 순서는 승격 후보 순서(findWaitingForUpdate)와 같다.
     */
    default List<Application> findWaitingIn(Collection<Long> helpRequestIds) {
        return findByHelpRequestIdInAndStatusInApplyOrder(helpRequestIds, ApplicationStatus.WAITING);
    }

    @Query("""
            select a from Application a
            where a.helpRequest.id in :helpRequestIds and a.status = :status
            order by a.helpRequest.id, a.appliedAt, a.id""")
    List<Application> findByHelpRequestIdInAndStatusInApplyOrder(
            @Param("helpRequestIds") Collection<Long> helpRequestIds,
            @Param("status") ApplicationStatus status);

    /**
     * 재알림 시각(식사 30분 전)이 된 승격 응답 대기의 ID. 보내고 나면 재알림 시각이 비어 다시 나오지 않는다.
     */
    default List<Long> findIdsToRemindPromotion(LocalDateTime now, int limit) {
        return findIdsByStatusAndRemindAtPassed(ApplicationStatus.PROMOTION_PENDING, now, PageRequest.of(0, limit));
    }

    @Query("""
            select a.id from Application a
            where a.status = :status and a.promotionRemindAt <= :now
            order by a.promotionRemindAt, a.id""")
    List<Long> findIdsByStatusAndRemindAtPassed(@Param("status") ApplicationStatus status,
                                                @Param("now") LocalDateTime now,
                                                Pageable pageable);

    /**
     * 응답 마감이 지난 승격 응답 대기의 ID (자동 거절 대상). 마감이 식사 시작인 것은 식사 시작 처리가 맡아 넣지 않는다.
     */
    default List<Long> findIdsPromotionExpired(LocalDateTime now, int limit) {
        return findIdsByStatusAndDeadlinePassed(ApplicationStatus.PROMOTION_PENDING, now, PageRequest.of(0, limit));
    }

    @Query("""
            select a.id from Application a
            where a.status = :status and a.promotionDeadline <= :now
              and a.promotionDeadline < a.helpRequest.startAt
            order by a.promotionDeadline, a.id""")
    List<Long> findIdsByStatusAndDeadlinePassed(@Param("status") ApplicationStatus status,
                                                @Param("now") LocalDateTime now,
                                                Pageable pageable);

    /**
     * 신청들의 지금 예비 인원 (관리자 전체 신청 현황). 예비가 없는 신청은 결과에 없다.
     */
    default List<HelpRequestApplicationCount> countWaitingByHelpRequest(Collection<Long> helpRequestIds) {
        return countByHelpRequestIdInAndStatus(helpRequestIds, ApplicationStatus.WAITING);
    }

    @Query("""
            select new com.hankkiatti.domain.application.repository.HelpRequestApplicationCount(a.helpRequest.id, count(a))
            from Application a
            where a.helpRequest.id in :helpRequestIds and a.status = :status
            group by a.helpRequest.id""")
    List<HelpRequestApplicationCount> countByHelpRequestIdInAndStatus(
            @Param("helpRequestIds") Collection<Long> helpRequestIds,
            @Param("status") ApplicationStatus status);

    /**
     * 신청들 중 예비가 승격돼 도우미 응답을 기다리는 신청의 ID (관리자 전체 신청 현황).
     */
    default List<Long> findHelpRequestIdsAwaitingPromotion(Collection<Long> helpRequestIds) {
        return findHelpRequestIdsByStatus(helpRequestIds, ApplicationStatus.PROMOTION_PENDING);
    }

    @Query("""
            select distinct a.helpRequest.id from Application a
            where a.helpRequest.id in :helpRequestIds and a.status = :status""")
    List<Long> findHelpRequestIdsByStatus(@Param("helpRequestIds") Collection<Long> helpRequestIds,
                                          @Param("status") ApplicationStatus status);

    /**
     * 신청들의 지원 전부를 도우미와 함께 (관리자 장애학생 상세 — 매칭현황·취소·노쇼 이력). 한 학생의 신청이라 많지 않다.
     */
    @Query("select a from Application a join fetch a.helper where a.helpRequest.id in :helpRequestIds")
    List<Application> findWithHelperByHelpRequestIdIn(@Param("helpRequestIds") Collection<Long> helpRequestIds);

    /**
     * 도우미의 지원 전부 (관리자 도우미 상세 요약 숫자).
     */
    List<Application> findByHelperAccountId(Long helperId);

    /**
     * 도우미들의 이용 완료 건수 (관리자 도우미 목록). 이용 완료가 없는 도우미는 결과에 없다.
     */
    default List<HelperApplicationCount> countCompletedByHelper(Collection<Long> helperIds) {
        return countByHelperIdInAndStatus(helperIds, ApplicationStatus.COMPLETED);
    }

    @Query("""
            select new com.hankkiatti.domain.application.repository.HelperApplicationCount(a.helper.accountId, count(a))
            from Application a
            where a.helper.accountId in :helperIds and a.status = :status
            group by a.helper.accountId""")
    List<HelperApplicationCount> countByHelperIdInAndStatus(@Param("helperIds") Collection<Long> helperIds,
                                                            @Param("status") ApplicationStatus status);

    /**
     * 도우미의 봉사시간 합계. 봉사시간이 기록된 지원(이용 완료 1.0, 노쇼 0)만 더한다. 하나도 없으면 null.
     */
    @Query("select sum(a.volunteerHours) from Application a where a.helper.accountId = :helperId")
    BigDecimal sumVolunteerHoursByHelperId(@Param("helperId") Long helperId);
}
