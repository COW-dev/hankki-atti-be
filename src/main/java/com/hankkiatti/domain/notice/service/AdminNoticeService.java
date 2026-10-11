package com.hankkiatti.domain.notice.service;

import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.notice.dto.request.AdminNoticeSaveRequestDto;
import com.hankkiatti.domain.notice.dto.response.AdminNoticeDeleteResponseDto;
import com.hankkiatti.domain.notice.dto.response.AdminNoticeDetailResponseDto;
import com.hankkiatti.domain.notice.dto.response.AdminNoticeResponseDto;
import com.hankkiatti.domain.notice.dto.response.AdminNoticesResponseDto;
import com.hankkiatti.domain.notice.entity.Notice;
import com.hankkiatti.domain.notice.event.NoticePublishedEvent;
import com.hankkiatti.domain.notice.exception.NoticeErrorType;
import com.hankkiatti.domain.notice.exception.NoticeException;
import com.hankkiatti.domain.notice.repository.NoticeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 공지 (BE-70, Figma AF-04). 목록·상세는 모든 관리자, 작성·수정·삭제는 전체 권한만.
 * 게시하면 대상에게 인앱 알림이 가고(NoticePublishedEvent), 수정은 다시 알리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNoticeService {

    public static final int PAGE_SIZE = 20;
    // 같은 관리자가 이 시간 안에 같은 제목으로 또 게시하면 연타·재전송으로 보고 막는다
    static final Duration DUPLICATE_WINDOW = Duration.ofSeconds(10);

    private final NoticeRepository noticeRepository;
    private final AdminRepository adminRepository;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminNoticesResponseDto getNotices(int page) {
        if (page < 0) {
            throw new NoticeException(NoticeErrorType.INVALID_PAGE, "page=" + page);
        }
        Page<Notice> notices = noticeRepository.findPublished(PageRequest.of(page, PAGE_SIZE));
        return new AdminNoticesResponseDto(
                notices.map(notice -> new AdminNoticeResponseDto(notice.getId(), notice.getTitle(), notice.getTarget(),
                        notice.isPinned(), notice.getPublishedAt(), notice.getEditedAt())).getContent(),
                page, PAGE_SIZE, notices.getTotalElements(), notices.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminNoticeDetailResponseDto getNotice(Long noticeId) {
        Notice notice = findNotice(noticeId);
        if (notice.isDeleted()) {
            throw new NoticeException(NoticeErrorType.DELETED, "noticeId=" + noticeId);
        }
        return toDetail(notice);
    }

    /**
     * 게시. 관리자 계정 행을 잠가 같은 관리자의 동시 요청을 한 줄로 세운 뒤 중복을 확인한다 — 동시에 두 번 눌러도 하나만 저장된다.
     */
    @Transactional
    public AdminNoticeDetailResponseDto create(Long adminAccountId, AdminNoticeSaveRequestDto request) {
        Admin admin = requireFullAdmin(adminAccountId);
        accountRepository.findByIdForUpdate(adminAccountId);

        LocalDateTime now = LocalDateTime.now(clock);
        String title = request.title().strip();
        if (noticeRepository.countRecentByAuthorAndTitle(adminAccountId, title, now.minus(DUPLICATE_WINDOW)) > 0) {
            throw new NoticeException(NoticeErrorType.DUPLICATE_SUBMIT, "adminAccountId=" + adminAccountId);
        }

        Notice notice = noticeRepository.save(new Notice(admin, title, request.body(), request.target(),
                Boolean.TRUE.equals(request.pinned()), now));
        eventPublisher.publishEvent(new NoticePublishedEvent(notice.getId()));
        log.info("공지 게시: noticeId={}, adminAccountId={}, target={}", notice.getId(), adminAccountId, notice.getTarget());
        return toDetail(notice);
    }

    // 수정은 알림을 다시 보내지 않는다 (대상이 바뀌어도 — 새 대상 알림은 #44)
    @Transactional
    public AdminNoticeDetailResponseDto update(Long adminAccountId, Long noticeId, AdminNoticeSaveRequestDto request) {
        requireFullAdmin(adminAccountId);
        Notice notice = findNoticeForUpdate(noticeId);
        notice.edit(request.title().strip(), request.body(), request.target(),
                Boolean.TRUE.equals(request.pinned()), LocalDateTime.now(clock));
        log.info("공지 수정: noticeId={}, adminAccountId={}, target={}", noticeId, adminAccountId, notice.getTarget());
        return toDetail(notice);
    }

    // 소프트 삭제 — 이미 받은 알림은 남고, 누르면 NOTICE_DELETED
    @Transactional
    public AdminNoticeDeleteResponseDto delete(Long adminAccountId, Long noticeId) {
        requireFullAdmin(adminAccountId);
        Notice notice = findNoticeForUpdate(noticeId);
        notice.delete(LocalDateTime.now(clock));
        log.info("공지 삭제: noticeId={}, adminAccountId={}", noticeId, adminAccountId);
        return new AdminNoticeDeleteResponseDto(notice.getId(), notice.getDeletedAt());
    }

    private Notice findNotice(Long noticeId) {
        return noticeRepository.findById(noticeId)
                .orElseThrow(() -> new NoticeException(NoticeErrorType.NOT_FOUND, "noticeId=" + noticeId));
    }

    // 수정·삭제는 공지 행을 잠그고 읽는다 — 동시에 오면 뒤 요청은 앞 요청이 끝난 상태를 본다
    private Notice findNoticeForUpdate(Long noticeId) {
        return noticeRepository.findByIdForUpdate(noticeId)
                .orElseThrow(() -> new NoticeException(NoticeErrorType.NOT_FOUND, "noticeId=" + noticeId));
    }

    private AdminNoticeDetailResponseDto toDetail(Notice notice) {
        return new AdminNoticeDetailResponseDto(notice.getId(), notice.getTitle(), notice.getBody(), notice.getTarget(),
                notice.isPinned(), notice.getPublishedAt(), notice.getEditedAt());
    }

    private Admin requireFullAdmin(Long adminAccountId) {
        Admin admin = adminRepository.findById(adminAccountId)
                .orElseThrow(() -> new AuthException(AuthErrorType.ACCESS_DENIED, "adminAccountId=" + adminAccountId));
        if (!admin.isFull()) {
            throw new AuthException(AuthErrorType.ACCESS_DENIED,
                    "adminAccountId=" + adminAccountId + ", grade=" + admin.getGrade());
        }
        return admin;
    }
}
