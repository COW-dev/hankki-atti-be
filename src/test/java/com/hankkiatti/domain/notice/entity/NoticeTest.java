package com.hankkiatti.domain.notice.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.entity.AdminGrade;
import com.hankkiatti.domain.notice.exception.NoticeErrorType;
import com.hankkiatti.domain.notice.exception.NoticeException;
import com.hankkiatti.support.TestAccounts;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NoticeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);

    private final Admin admin = new Admin(TestAccounts.withId(90L, AccountRole.ADMIN, "hash", false), "홍길동",
            AdminGrade.FULL);

    private Notice notice() {
        return new Notice(admin, "10월 학식당 운영 시간 안내", "본문", NoticeTarget.ALL, true, NOW);
    }

    @Test
    void 생성_게시시각기록_수정삭제시각없음() {
        // when
        Notice notice = notice();

        // then
        assertThat(notice.getPublishedAt()).isEqualTo(NOW);
        assertThat(notice.getEditedAt()).isNull();
        assertThat(notice.isDeleted()).isFalse();
    }

    @Test
    void edit_내용을바꾸고수정시각기록_게시시각유지() {
        // given
        Notice notice = notice();

        // when
        notice.edit("도우미 취소 기준 안내", "새 본문", NoticeTarget.HELPER, false, NOW.plusDays(1));

        // then
        assertThat(notice.getTitle()).isEqualTo("도우미 취소 기준 안내");
        assertThat(notice.getBody()).isEqualTo("새 본문");
        assertThat(notice.getTarget()).isEqualTo(NoticeTarget.HELPER);
        assertThat(notice.isPinned()).isFalse();
        assertThat(notice.getEditedAt()).isEqualTo(NOW.plusDays(1));
        assertThat(notice.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void edit_삭제된공지_DELETED() {
        // given
        Notice notice = notice();
        notice.delete(NOW.plusHours(1));

        // when & then
        assertThatThrownBy(() -> notice.edit("제목", "본문", NoticeTarget.ALL, false, NOW.plusHours(2)))
                .isInstanceOf(NoticeException.class)
                .extracting("errorCode").isEqualTo(NoticeErrorType.DELETED);
        assertThat(notice.getEditedAt()).isNull();
    }

    @Test
    void delete_삭제시각기록() {
        // given
        Notice notice = notice();

        // when
        notice.delete(NOW.plusHours(1));

        // then
        assertThat(notice.isDeleted()).isTrue();
        assertThat(notice.getDeletedAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    void delete_이미삭제_DELETED_처음삭제시각유지() {
        // given
        Notice notice = notice();
        notice.delete(NOW.plusHours(1));

        // when & then
        assertThatThrownBy(() -> notice.delete(NOW.plusHours(2)))
                .isInstanceOf(NoticeException.class)
                .extracting("errorCode").isEqualTo(NoticeErrorType.DELETED);
        assertThat(notice.getDeletedAt()).isEqualTo(NOW.plusHours(1));
    }
}
