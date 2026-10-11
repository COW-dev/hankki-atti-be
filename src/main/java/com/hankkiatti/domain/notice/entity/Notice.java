package com.hankkiatti.domain.notice.entity;

import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.common.BaseTimeEntity;
import com.hankkiatti.domain.notice.exception.NoticeErrorType;
import com.hankkiatti.domain.notice.exception.NoticeException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지 (BE-70·71). 저장하면 바로 게시된다 — 임시저장·예약 게시는 #42.
 * 게시일·수정일은 Clock 기준으로 직접 기록한다: createdAt·updatedAt은 생성 때 함께 채워져 "수정 안 함(-)"을 나타낼 수 없고,
 * 감사(auditing) 시각은 Clock을 따르지 않아 중복 게시 판정에 쓸 수 없다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "notices",
        indexes = @Index(name = "idx_notices_deleted_at_pinned_published_at",
                columnList = "deleted_at, pinned, published_at")
)
public class Notice extends BaseTimeEntity {

    public static final int TITLE_MAX_LENGTH = 100;
    public static final int BODY_MAX_LENGTH = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Admin author;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    // 일반 텍스트 (줄바꿈 보존). 링크는 프론트가 URL을 자동 연결한다
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 20)
    private NoticeTarget target;

    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false)
    private LocalDateTime publishedAt;

    // 관리자가 수정했을 때만 채운다 (null이면 화면에 "-")
    private LocalDateTime editedAt;

    private LocalDateTime deletedAt;

    public Notice(Admin author, String title, String body, NoticeTarget target, boolean pinned, LocalDateTime now) {
        this.author = author;
        this.title = title;
        this.body = body;
        this.target = target;
        this.pinned = pinned;
        this.publishedAt = now;
    }

    public void edit(String title, String body, NoticeTarget target, boolean pinned, LocalDateTime now) {
        requireNotDeleted();
        this.title = title;
        this.body = body;
        this.target = target;
        this.pinned = pinned;
        this.editedAt = now;
    }

    public void delete(LocalDateTime now) {
        requireNotDeleted();
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void requireNotDeleted() {
        if (isDeleted()) {
            throw new NoticeException(NoticeErrorType.DELETED, "noticeId=" + id);
        }
    }
}
