package com.hankkiatti.domain.helper.repository;

import com.hankkiatti.domain.helper.entity.Helper;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HelperRepository extends JpaRepository<Helper, Long> {

    boolean existsByEmail(String email);

    boolean existsByStudentNo(String studentNo);

    /**
     * 도우미 행을 잠그고 가져온다. 같은 도우미가 겹치는 두 신청에 동시에 지원해 둘 다 매칭되지 않도록 한 줄로 세운다.
     * 신청 행 락 다음에 잡는다 (락 순서: 신청 → 도우미).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Helper h where h.accountId = :accountId")
    Optional<Helper> findByIdForUpdate(@Param("accountId") Long accountId);
}
