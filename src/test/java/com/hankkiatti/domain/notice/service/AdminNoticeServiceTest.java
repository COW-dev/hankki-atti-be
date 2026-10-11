package com.hankkiatti.domain.notice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.entity.AdminGrade;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.notice.dto.request.AdminNoticeSaveRequestDto;
import com.hankkiatti.domain.notice.dto.response.AdminNoticeDeleteResponseDto;
import com.hankkiatti.domain.notice.dto.response.AdminNoticeDetailResponseDto;
import com.hankkiatti.domain.notice.dto.response.AdminNoticesResponseDto;
import com.hankkiatti.domain.notice.entity.Notice;
import com.hankkiatti.domain.notice.entity.NoticeTarget;
import com.hankkiatti.domain.notice.event.NoticePublishedEvent;
import com.hankkiatti.domain.notice.exception.NoticeErrorType;
import com.hankkiatti.domain.notice.exception.NoticeException;
import com.hankkiatti.domain.notice.repository.NoticeRepository;
import com.hankkiatti.support.TestAccounts;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminNoticeServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final Long ADMIN_ID = 90L;
    private static final Long NOTICE_ID = 70L;

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AdminNoticeService service;

    private final Admin fullAdmin = admin(AdminGrade.FULL);

    @BeforeEach
    void setUp() {
        service = new AdminNoticeService(noticeRepository, adminRepository, accountRepository, eventPublisher,
                Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
    }

    private static Admin admin(AdminGrade grade) {
        Admin admin = new Admin(TestAccounts.withId(ADMIN_ID, AccountRole.ADMIN, "hash", false), "홍길동", grade);
        ReflectionTestUtils.setField(admin, "accountId", ADMIN_ID);
        return admin;
    }

    private Notice notice(LocalDateTime publishedAt) {
        Notice notice = new Notice(fullAdmin, "10월 학식당 운영 시간 안내", "본문", NoticeTarget.ALL, true, publishedAt);
        ReflectionTestUtils.setField(notice, "id", NOTICE_ID);
        return notice;
    }

    private static AdminNoticeSaveRequestDto request(String title, Boolean pinned) {
        return new AdminNoticeSaveRequestDto(title, "10월부터 운영 시간이 바뀌어요.\n\n• 평일 11:00 ~ 14:00",
                NoticeTarget.HELPER, pinned);
    }

    private void givenSaveAssignsId() {
        given(noticeRepository.save(any(Notice.class))).willAnswer(invocation -> {
            Notice saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", NOTICE_ID);
            return saved;
        });
    }

    @Test
    void create_전체권한_관리자계정을잠그고저장한뒤게시이벤트발행() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(fullAdmin));
        givenSaveAssignsId();

        // when
        AdminNoticeDetailResponseDto result = service.create(ADMIN_ID, request("  10월 학식당 운영 시간 안내  ", null));

        // then
        verify(accountRepository).findByIdForUpdate(ADMIN_ID);
        verify(noticeRepository).countRecentByAuthorAndTitle(ADMIN_ID, "10월 학식당 운영 시간 안내", NOW.minusSeconds(10));
        verify(eventPublisher).publishEvent(new NoticePublishedEvent(NOTICE_ID));
        assertThat(result.noticeId()).isEqualTo(NOTICE_ID);
        assertThat(result.title()).isEqualTo("10월 학식당 운영 시간 안내");
        assertThat(result.body()).isEqualTo("10월부터 운영 시간이 바뀌어요.\n\n• 평일 11:00 ~ 14:00");
        assertThat(result.target()).isEqualTo(NoticeTarget.HELPER);
        assertThat(result.pinned()).isFalse();
        assertThat(result.publishedAt()).isEqualTo(NOW);
        assertThat(result.editedAt()).isNull();
    }

    @Test
    void create_작성자는로그인한관리자() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(fullAdmin));
        givenSaveAssignsId();

        // when
        service.create(ADMIN_ID, request("공지", true));

        // then
        ArgumentCaptor<Notice> saved = ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(saved.capture());
        assertThat(saved.getValue().getAuthor()).isSameAs(fullAdmin);
        assertThat(saved.getValue().isPinned()).isTrue();
    }

    @Test
    void create_10초안에같은제목_DUPLICATE_SUBMIT_저장안함() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(fullAdmin));
        given(noticeRepository.countRecentByAuthorAndTitle(ADMIN_ID, "공지", NOW.minusSeconds(10))).willReturn(1L);

        // when & then
        assertThatThrownBy(() -> service.create(ADMIN_ID, request("공지", false)))
                .isInstanceOf(NoticeException.class)
                .extracting("errorCode").isEqualTo(NoticeErrorType.DUPLICATE_SUBMIT);
        verify(noticeRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void create_제한권한_ACCESS_DENIED_잠그지도않음() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin(AdminGrade.LIMITED)));

        // when & then
        assertThatThrownBy(() -> service.create(ADMIN_ID, request("공지", false)))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
        verify(accountRepository, never()).findByIdForUpdate(anyLong());
        verify(noticeRepository, never()).save(any());
    }

    @Test
    void create_관리자프로필없음_ACCESS_DENIED() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.create(ADMIN_ID, request("공지", false)))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
    }

    @Test
    void update_내용을바꾸고수정시각기록_알림이벤트없음() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(fullAdmin));
        given(noticeRepository.findByIdForUpdate(NOTICE_ID)).willReturn(Optional.of(notice(NOW.minusDays(1))));

        // when
        AdminNoticeDetailResponseDto result = service.update(ADMIN_ID, NOTICE_ID, request(" 도우미 안내 ", true));

        // then
        assertThat(result.title()).isEqualTo("도우미 안내");
        assertThat(result.target()).isEqualTo(NoticeTarget.HELPER);
        assertThat(result.pinned()).isTrue();
        assertThat(result.publishedAt()).isEqualTo(NOW.minusDays(1));
        assertThat(result.editedAt()).isEqualTo(NOW);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void update_제한권한_ACCESS_DENIED() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin(AdminGrade.LIMITED)));

        // when & then
        assertThatThrownBy(() -> service.update(ADMIN_ID, NOTICE_ID, request("공지", false)))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
        verify(noticeRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void update_없는공지_NOT_FOUND() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(fullAdmin));
        given(noticeRepository.findByIdForUpdate(NOTICE_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.update(ADMIN_ID, NOTICE_ID, request("공지", false)))
                .isInstanceOf(NoticeException.class)
                .extracting("errorCode").isEqualTo(NoticeErrorType.NOT_FOUND);
    }

    @Test
    void update_삭제된공지_DELETED() {
        // given
        Notice deleted = notice(NOW.minusDays(1));
        deleted.delete(NOW.minusHours(1));
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(fullAdmin));
        given(noticeRepository.findByIdForUpdate(NOTICE_ID)).willReturn(Optional.of(deleted));

        // when & then
        assertThatThrownBy(() -> service.update(ADMIN_ID, NOTICE_ID, request("공지", false)))
                .isInstanceOf(NoticeException.class)
                .extracting("errorCode").isEqualTo(NoticeErrorType.DELETED);
    }

    @Test
    void delete_삭제시각을기록하고돌려줌() {
        // given
        Notice notice = notice(NOW.minusDays(1));
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(fullAdmin));
        given(noticeRepository.findByIdForUpdate(NOTICE_ID)).willReturn(Optional.of(notice));

        // when
        AdminNoticeDeleteResponseDto result = service.delete(ADMIN_ID, NOTICE_ID);

        // then
        assertThat(result.noticeId()).isEqualTo(NOTICE_ID);
        assertThat(result.deletedAt()).isEqualTo(NOW);
        assertThat(notice.isDeleted()).isTrue();
    }

    @Test
    void delete_제한권한_ACCESS_DENIED() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin(AdminGrade.LIMITED)));

        // when & then
        assertThatThrownBy(() -> service.delete(ADMIN_ID, NOTICE_ID))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
    }

    @Test
    void getNotices_한줄씩옮기고페이지정보() {
        // given
        Notice notice = notice(NOW);
        given(noticeRepository.findPublished(PageRequest.of(0, 20)))
                .willReturn(new PageImpl<>(List.of(notice), PageRequest.of(0, 20), 1));

        // when
        AdminNoticesResponseDto result = service.getNotices(0);

        // then
        assertThat(result.notices()).singleElement().satisfies(row -> {
            assertThat(row.noticeId()).isEqualTo(NOTICE_ID);
            assertThat(row.target()).isEqualTo(NoticeTarget.ALL);
            assertThat(row.pinned()).isTrue();
            assertThat(row.publishedAt()).isEqualTo(NOW);
            assertThat(row.editedAt()).isNull();
        });
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void getNotices_음수페이지_INVALID_PAGE() {
        assertThatThrownBy(() -> service.getNotices(-1))
                .isInstanceOf(NoticeException.class)
                .extracting("errorCode").isEqualTo(NoticeErrorType.INVALID_PAGE);
        verifyNoInteractions(noticeRepository);
    }

    @Test
    void getNotice_본문까지() {
        // given
        given(noticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(notice(NOW)));

        // when
        AdminNoticeDetailResponseDto result = service.getNotice(NOTICE_ID);

        // then
        assertThat(result.body()).isEqualTo("본문");
    }

    @Test
    void getNotice_삭제된공지_DELETED() {
        // given
        Notice deleted = notice(NOW);
        deleted.delete(NOW.plusHours(1));
        given(noticeRepository.findById(NOTICE_ID)).willReturn(Optional.of(deleted));

        // when & then
        assertThatThrownBy(() -> service.getNotice(NOTICE_ID))
                .isInstanceOf(NoticeException.class)
                .extracting("errorCode").isEqualTo(NoticeErrorType.DELETED);
    }

    @Test
    void getNotice_없는공지_NOT_FOUND() {
        // given
        given(noticeRepository.findById(NOTICE_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getNotice(NOTICE_ID))
                .isInstanceOf(NoticeException.class)
                .extracting("errorCode").isEqualTo(NoticeErrorType.NOT_FOUND);
        verify(noticeRepository, never()).countRecentByAuthorAndTitle(anyLong(), anyString(), any());
    }
}
