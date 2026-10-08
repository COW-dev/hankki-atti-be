package com.hankkiatti.domain.application.repository;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByHelpRequestIdAndStatus(Long helpRequestId, ApplicationStatus status);

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
     * 도우미의 봉사시간 합계. 봉사시간이 기록된 지원(이용 완료 1.0, 노쇼 0)만 더한다. 하나도 없으면 null.
     */
    @Query("select sum(a.volunteerHours) from Application a where a.helper.accountId = :helperId")
    BigDecimal sumVolunteerHoursByHelperId(@Param("helperId") Long helperId);
}
