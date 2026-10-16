package com.hankkiatti.domain.student.repository;

import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {

    boolean existsByStudentNo(String studentNo);

    /**
     * 관리자 장애학생 목록. 조건이 null이면 거르지 않는다. pattern은 이름·학번 부분 일치(%q%, 이름은 대소문자 무시). 이름·학번순.
     */
    @Query(value = """
            select s from Student s join fetch s.account a
            where (:pattern is null or lower(s.name) like lower(:pattern) or s.studentNo like :pattern)
              and (:disabilityType is null or s.disabilityType = :disabilityType)
              and (:status is null or a.status = :status)
            order by s.name, s.studentNo""",
            countQuery = """
            select count(s) from Student s join s.account a
            where (:pattern is null or lower(s.name) like lower(:pattern) or s.studentNo like :pattern)
              and (:disabilityType is null or s.disabilityType = :disabilityType)
              and (:status is null or a.status = :status)""")
    Page<Student> search(@Param("pattern") String pattern,
                         @Param("disabilityType") DisabilityType disabilityType,
                         @Param("status") AccountStatus status,
                         Pageable pageable);

    /**
     * 장애학생 행을 잠그고 가져온다. 같은 장애학생의 신청 생성은 이 락으로 한 줄로 선다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.accountId = :accountId")
    Optional<Student> findByIdForUpdate(@Param("accountId") Long accountId);
}
