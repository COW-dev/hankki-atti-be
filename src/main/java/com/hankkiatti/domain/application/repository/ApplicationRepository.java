package com.hankkiatti.domain.application.repository;

import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findByHelpRequestIdAndStatus(Long helpRequestId, ApplicationStatus status);

    /**
     * 도우미의 봉사시간 합계. 봉사시간이 기록된 지원(이용 완료 1.0, 노쇼 0)만 더한다. 하나도 없으면 null.
     */
    @Query("select sum(a.volunteerHours) from Application a where a.helper.accountId = :helperId")
    BigDecimal sumVolunteerHoursByHelperId(@Param("helperId") Long helperId);
}
