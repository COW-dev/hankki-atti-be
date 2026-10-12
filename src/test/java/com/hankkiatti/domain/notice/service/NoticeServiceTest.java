package com.hankkiatti.domain.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.entity.AdminGrade;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.notice.dto.response.NoticeDetailResponseDto;
import com.hankkiatti.domain.notice.dto.response.NoticesResponseDto;
import com.hankkiatti.domain.notice.entity.Notice;
import com.hankkiatti.domain.notice.entity.NoticeTarget;
import com.hankkiatti.domain.notice.exception.NoticeErrorType;
import com.hankkiatti.domain.notice.exception.NoticeException;
import com.hankkiatti.domain.notice.repository.NoticeRepository;
import com.hankkiatti.support.TestAccounts;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final Long STUDENT_ID = 1L;
    private static final Long HELPER_ID = 7L;
    private static final Long NOTICE_ID = 70L;
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20);

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private HelperRepository helperRepository;

    private NoticeService service;

    private final Admin admin = new Admin(TestAccounts.withId(90L, AccountRole.ADMIN, "hash", false), "홍길동",
            AdminGrade.FULL);

    @BeforeEach
    void setUp() {
        service = new NoticeService(noticeRepository, helperRepository);
    }

    private Notice notice(NoticeTarget target) {
        Notice notice = new Notice(admin, "아띠 정기 회의", "본문", target, false, NOW);
        ReflectionTestUtils.setField(notice, "id", NOTICE_ID);
        return notice;
    }

    private void givenHelper(boolean attiMember) {
        Account account = TestAccounts.withId(HELPER_ID, AccountRole.HELPER, "hash", false);
        Helper helper = new Helper(account, "이도움", "60230001", "helper@mju.ac.kr", "010-1111-1111", "kakao",
                attiMember, NOW);
        given(helperRepository.findById(HELPER_ID)).willReturn(Optional.of(helper));
    }

    @Test
    void getNotices_장애학생_전체와장애학생대상으로조회() {
        // given
        given(noticeRepository.findPublishedByTargetIn(List.of(NoticeTarget.ALL, NoticeTarget.STUDENT), FIRST_PAGE))
                .willReturn(new PageImpl<>(List.of(notice(NoticeTarget.STUDENT)), FIRST_PAGE, 1));

        // when
        NoticesResponseDto result = service.getNotices(STUDENT_ID, AccountRole.STUDENT, 0);

        // then
        assertThat(result.notices()).singleElement().satisfies(row -> {
            assertThat(row.noticeId()).isEqualTo(NOTICE_ID);
            assertThat(row.title()).isEqualTo("아띠 정기 회의");
            assertThat(row.publishedAt()).isEqualTo(NOW);
        });
        assertThat(result.totalCount()).isEqualTo(1);
        verifyNoInteractions(helperRepository);
    }

    @Test
    void getNotices_아띠소속도우미_아띠대상까지조회() {
        // given
        givenHelper(true);
        given(noticeRepository.findPublishedByTargetIn(
                List.of(NoticeTarget.ALL, NoticeTarget.HELPER, NoticeTarget.ATTI), FIRST_PAGE))
                .willReturn(new PageImpl<>(List.of(), FIRST_PAGE, 0));

        // when
        NoticesResponseDto result = service.getNotices(HELPER_ID, AccountRole.HELPER, 0);

        // then
        assertThat(result.notices()).isEmpty();
    }

    @Test
    void getNotices_음수페이지_INVALID_PAGE() {
        assertThatThrownBy(() -> service.getNotices(STUDENT_ID, AccountRole.STUDENT, -1))
                .isInstanceOf(NoticeException.class)
                .extracting("errorCode").isEqualTo(NoticeErrorType.INVALID_PAGE);
        verifyNoInteractions(noticeRepository);
    }

    @Test
    void getNotice_볼수있는공지_본문대상수정시각까지() {
        // given
        Notice notice = notice(NoticeTarget.ATTI);
        notice.edit("아띠 정기 회의 (장소 변경)", "새 본문", NoticeTarget.ATTI, true, NOW.plusHours(1));
        givenHelper(true);
        given(noticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));

        // when
        NoticeDetailResponseDto result = service.getNotice(HELPER_ID, AccountRole.HELPER, NOTICE_ID);

        // then
        assertThat(result.title()).isEqualTo("아띠 정기 회의 (장소 변경)");
        assertThat(result.body()).isEqualTo("새 본문");
        assertThat(result.target()).isEqualTo(NoticeTarget.ATTI);
        assertThat(result.pinned()).isTrue();
        assertThat(result.editedAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    void getNotice_아띠비소속도우미가아띠공지_NOT_FOUND() {
        // given
        givenHelper(false);
        given(noticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice(NoticeTarget.ATTI)));

        // when & then
        assertThatThrownBy(() -> service.getNotice(HELPER_ID, AccountRole.HELPER, NOTICE_ID))
                .isInstanceOf(NoticeException.class)
                .extracting("errorCode").isEqualTo(NoticeErrorType.NOT_FOUND);
    }

    @Test
    void getNotice_장애학생이도우미공지_삭제됐어도_NOT_FOUND() {
        // given — 대상이 아닌 공지는 삭제 여부도 알리지 않는다
        Notice notice = notice(NoticeTarget.HELPER);
        notice.delete(NOW.plusHours(1));
        given(noticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));

        // when & then
        assertThatThrownBy(() -> service.getNotice(STUDENT_ID, AccountRole.STUDENT, NOTICE_ID))
                .isInstanceOf(NoticeException.class)
                .extracting("errorCode").isEqualTo(NoticeErrorType.NOT_FOUND);
    }

    @Test
    void getNotice_볼수있던공지가삭제됨_DELETED() {
        // given
        Notice notice = notice(NoticeTarget.ALL);
        notice.delete(NOW.plusHours(1));
        given(noticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice));

        // when & then
        assertThatThrownBy(() -> service.getNotice(STUDENT_ID, AccountRole.STUDENT, NOTICE_ID))
                .isInstanceOf(NoticeException.class)
                .extracting("errorCode").isEqualTo(NoticeErrorType.DELETED);
    }

    @Test
    void getNotice_없는공지_NOT_FOUND() {
        // given
        given(noticeRepository.findById(NOTICE_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getNotice(STUDENT_ID, AccountRole.STUDENT, NOTICE_ID))
                .isInstanceOf(NoticeException.class)
                .extracting("errorCode").isEqualTo(NoticeErrorType.NOT_FOUND);
        verify(helperRepository, never()).findById(anyLong());
    }
}
