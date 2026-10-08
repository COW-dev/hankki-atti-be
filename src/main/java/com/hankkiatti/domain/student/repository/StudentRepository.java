package com.hankkiatti.domain.student.repository;

import com.hankkiatti.domain.student.entity.Student;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long> {

    boolean existsByStudentNo(String studentNo);

    /**
     * 장애학생 행을 잠그고 가져온다. 같은 장애학생의 신청 생성은 이 락으로 한 줄로 선다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.accountId = :accountId")
    Optional<Student> findByIdForUpdate(@Param("accountId") Long accountId);
}
