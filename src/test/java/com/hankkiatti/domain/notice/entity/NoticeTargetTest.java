package com.hankkiatti.domain.notice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.AccountRole;
import org.junit.jupiter.api.Test;

class NoticeTargetTest {

    @Test
    void visibleTo_장애학생_전체와장애학생() {
        assertThat(NoticeTarget.visibleTo(AccountRole.STUDENT, false))
                .containsExactlyInAnyOrder(NoticeTarget.ALL, NoticeTarget.STUDENT);
    }

    @Test
    void visibleTo_아띠비소속도우미_전체와도우미() {
        assertThat(NoticeTarget.visibleTo(AccountRole.HELPER, false))
                .containsExactlyInAnyOrder(NoticeTarget.ALL, NoticeTarget.HELPER);
    }

    @Test
    void visibleTo_아띠소속도우미_아띠까지() {
        assertThat(NoticeTarget.visibleTo(AccountRole.HELPER, true))
                .containsExactlyInAnyOrder(NoticeTarget.ALL, NoticeTarget.HELPER, NoticeTarget.ATTI);
    }

    @Test
    void visibleTo_관리자_없음() {
        assertThat(NoticeTarget.visibleTo(AccountRole.ADMIN, false)).isEmpty();
    }
}
