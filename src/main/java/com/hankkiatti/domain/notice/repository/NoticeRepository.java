package com.hankkiatti.domain.notice.repository;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.notice.entity.Notice;
import com.hankkiatti.domain.notice.entity.NoticeTarget;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /**
     * 공지 행을 잠그고 가져온다. 수정·삭제가 동시에 와도 한 줄로 세워, 먼저 읽은 수정이 삭제를 덮어써 공지가 되살아나지 않게 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notice n where n.id = :id")
    Optional<Notice> findByIdForUpdate(@Param("id") Long id);

    /**
     * 관리자 목록 — 삭제된 공지 빼고, 고정 먼저, 그다음 최신 게시순.
     */
    @Query(value = """
            select n from Notice n
            where n.deletedAt is null
            order by n.pinned desc, n.publishedAt desc, n.id desc""",
            countQuery = "select count(n) from Notice n where n.deletedAt is null")
    Page<Notice> findPublished(Pageable pageable);

    /**
     * 사용자 목록 — 볼 수 있는 대상의 공지만, 정렬은 관리자 목록과 같다.
     */
    @Query(value = """
            select n from Notice n
            where n.deletedAt is null and n.target in :targets
            order by n.pinned desc, n.publishedAt desc, n.id desc""",
            countQuery = "select count(n) from Notice n where n.deletedAt is null and n.target in :targets")
    Page<Notice> findPublishedByTargetIn(@Param("targets") Collection<NoticeTarget> targets, Pageable pageable);

    /**
     * 같은 관리자가 since 이후 같은 제목으로 게시한(삭제 안 된) 공지 수 — 저장 버튼 연타·재전송 차단용.
     */
    @Query("""
            select count(n) from Notice n
            where n.author.accountId = :authorId and n.title = :title
              and n.deletedAt is null and n.publishedAt > :since""")
    long countRecentByAuthorAndTitle(@Param("authorId") Long authorId,
                                     @Param("title") String title,
                                     @Param("since") LocalDateTime since);

    /**
     * 게시 알림을 받을 계정 ID — 활성 계정만, 관리자는 대상이 아니다 (알림은 장애학생·도우미만 받는다).
     */
    default List<Long> findRecipientAccountIds(NoticeTarget target) {
        return switch (target) {
            case ALL -> findAccountIds(List.of(AccountRole.STUDENT, AccountRole.HELPER), AccountStatus.ACTIVE);
            case STUDENT -> findAccountIds(List.of(AccountRole.STUDENT), AccountStatus.ACTIVE);
            case HELPER -> findAccountIds(List.of(AccountRole.HELPER), AccountStatus.ACTIVE);
            case ATTI -> findAttiHelperAccountIds(AccountStatus.ACTIVE);
        };
    }

    @Query("select a.id from Account a where a.role in :roles and a.status = :status order by a.id")
    List<Long> findAccountIds(@Param("roles") Collection<AccountRole> roles, @Param("status") AccountStatus status);

    @Query("""
            select h.accountId from Helper h join h.account a
            where h.attiMember = true and a.status = :status
            order by h.accountId""")
    List<Long> findAttiHelperAccountIds(@Param("status") AccountStatus status);
}
