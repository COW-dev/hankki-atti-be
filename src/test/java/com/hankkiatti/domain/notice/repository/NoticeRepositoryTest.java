package com.hankkiatti.domain.notice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.entity.AdminGrade;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.notice.entity.Notice;
import com.hankkiatti.domain.notice.entity.NoticeTarget;
import com.hankkiatti.global.config.JpaConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
// DomainRepositoryTest와 같은 이유로 테스트 설정의 H2(MySQL 모드)를 그대로 쓴다
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class NoticeRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private HelperRepository helperRepository;

    private Admin admin;

    @BeforeEach
    void setUp() {
        admin = adminRepository.save(new Admin(saveAccount("notice-admin", AccountRole.ADMIN), "홍길동", AdminGrade.FULL));
    }

    private Account saveAccount(String loginId, AccountRole role) {
        return accountRepository.save(new Account(loginId, "hash", role, false, false));
    }

    private Long saveHelper(String email, boolean attiMember) {
        Account account = saveAccount(email, AccountRole.HELPER);
        return helperRepository.save(new Helper(account, "이도움", email.substring(0, 8), email, "010-1111-1111",
                "kakao", attiMember, NOW)).getAccountId();
    }

    private Notice save(String title, NoticeTarget target, boolean pinned, LocalDateTime publishedAt) {
        return noticeRepository.save(new Notice(admin, title, "본문", target, pinned, publishedAt));
    }

    @Test
    void findPublished_고정먼저그다음최신게시순_삭제제외() {
        // given
        save("오래된 일반", NoticeTarget.ALL, false, NOW.minusDays(3));
        save("최근 일반", NoticeTarget.HELPER, false, NOW);
        save("오래된 고정", NoticeTarget.STUDENT, true, NOW.minusDays(5));
        save("삭제됨", NoticeTarget.ALL, true, NOW.plusDays(1)).delete(NOW.plusDays(1));

        // when
        List<Notice> found = noticeRepository.findPublished(PageRequest.of(0, 20)).getContent();

        // then
        assertThat(found).extracting(Notice::getTitle).containsExactly("오래된 고정", "최근 일반", "오래된 일반");
    }

    @Test
    void findPublishedByTargetIn_볼수있는대상만_전체수도대상기준() {
        // given
        save("전체", NoticeTarget.ALL, false, NOW.minusDays(1));
        save("장애학생", NoticeTarget.STUDENT, false, NOW);
        save("아띠", NoticeTarget.ATTI, true, NOW);

        // when
        var page = noticeRepository.findPublishedByTargetIn(List.of(NoticeTarget.ALL, NoticeTarget.STUDENT),
                PageRequest.of(0, 20));

        // then
        assertThat(page.getContent()).extracting(Notice::getTitle).containsExactly("장애학생", "전체");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void countRecentByAuthorAndTitle_기간안의같은제목만_삭제된공지는뺌() {
        // given
        save("공지", NoticeTarget.ALL, false, NOW.minusSeconds(5));
        save("다른 제목", NoticeTarget.ALL, false, NOW.minusSeconds(5));
        save("오래된 공지", NoticeTarget.ALL, false, NOW.minusSeconds(30));
        save("지운 공지", NoticeTarget.ALL, false, NOW.minusSeconds(5)).delete(NOW);

        // when & then
        LocalDateTime since = NOW.minusSeconds(10);
        assertThat(noticeRepository.countRecentByAuthorAndTitle(admin.getAccountId(), "공지", since)).isEqualTo(1);
        assertThat(noticeRepository.countRecentByAuthorAndTitle(admin.getAccountId(), "오래된 공지", since)).isZero();
        assertThat(noticeRepository.countRecentByAuthorAndTitle(admin.getAccountId(), "지운 공지", since)).isZero();
        assertThat(noticeRepository.countRecentByAuthorAndTitle(admin.getAccountId() + 1, "공지", since)).isZero();
    }

    @Test
    void findRecipientAccountIds_대상별활성계정만_관리자제외() {
        // given
        Long student = saveAccount("60230001", AccountRole.STUDENT).getId();
        Account inactiveStudent = saveAccount("60230002", AccountRole.STUDENT);
        inactiveStudent.deactivate(NOW);
        Long attiHelper = saveHelper("atti0001@mju.ac.kr", true);
        Long helper = saveHelper("help0001@mju.ac.kr", false);
        Long inactiveAttiHelper = saveHelper("atti0002@mju.ac.kr", true);
        accountRepository.findById(inactiveAttiHelper).orElseThrow().deactivate(NOW);
        accountRepository.flush();

        // when & then
        assertThat(noticeRepository.findRecipientAccountIds(NoticeTarget.ALL))
                .containsExactlyInAnyOrder(student, attiHelper, helper);
        assertThat(noticeRepository.findRecipientAccountIds(NoticeTarget.STUDENT)).containsExactly(student);
        assertThat(noticeRepository.findRecipientAccountIds(NoticeTarget.HELPER))
                .containsExactlyInAnyOrder(attiHelper, helper);
        assertThat(noticeRepository.findRecipientAccountIds(NoticeTarget.ATTI)).containsExactly(attiHelper);
    }
}
