package com.hankkiatti.domain.application.repository;

import com.hankkiatti.domain.application.entity.VolunteerHourChange;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VolunteerHourChangeRepository extends JpaRepository<VolunteerHourChange, Long> {

    /**
     * 지원들의 봉사시간 수정 이력을 수정한 관리자와 함께, 최근순 (관리자 도우미 활동 이력).
     */
    @Query("""
            select c from VolunteerHourChange c join fetch c.admin
            where c.application.id in :applicationIds
            order by c.createdAt desc, c.id desc""")
    List<VolunteerHourChange> findWithAdminByApplicationIdIn(@Param("applicationIds") Collection<Long> applicationIds);
}
