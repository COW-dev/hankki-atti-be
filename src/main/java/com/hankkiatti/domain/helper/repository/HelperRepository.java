package com.hankkiatti.domain.helper.repository;

import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.helper.entity.Helper;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HelperRepository extends JpaRepository<Helper, Long> {

    boolean existsByEmail(String email);

    /**
     * 관리자 도우미 목록. 조건이 null이면 거르지 않는다. pattern은 이름·학번 부분 일치(%q%). 가입 최근순.
     */
    @Query(value = """
            select h from Helper h join fetch h.account a
            where (:pattern is null or h.name like :pattern or h.studentNo like :pattern)
              and (:status is null or a.status = :status)
              and (:attiMember is null or h.attiMember = :attiMember)
            order by h.createdAt desc, h.accountId desc""",
            countQuery = """
            select count(h) from Helper h join h.account a
            where (:pattern is null or h.name like :pattern or h.studentNo like :pattern)
              and (:status is null or a.status = :status)
              and (:attiMember is null or h.attiMember = :attiMember)""")
    Page<Helper> searchForAdmin(@Param("pattern") String pattern,
                                @Param("status") AccountStatus status,
                                @Param("attiMember") Boolean attiMember,
                                Pageable pageable);

    boolean existsByStudentNo(String studentNo);

    /**
     * 도우미 행을 잠그고 가져온다. 같은 도우미가 겹치는 두 신청에 동시에 지원해 둘 다 매칭되지 않도록 한 줄로 세운다.
     * 신청 행 락 다음에 잡는다 (락 순서: 신청 → 도우미).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Helper h where h.accountId = :accountId")
    Optional<Helper> findByIdForUpdate(@Param("accountId") Long accountId);
}
