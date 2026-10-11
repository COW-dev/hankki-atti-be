package com.hankkiatti.domain.notice.service;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.notice.dto.response.NoticeDetailResponseDto;
import com.hankkiatti.domain.notice.dto.response.NoticeResponseDto;
import com.hankkiatti.domain.notice.dto.response.NoticesResponseDto;
import com.hankkiatti.domain.notice.entity.Notice;
import com.hankkiatti.domain.notice.entity.NoticeTarget;
import com.hankkiatti.domain.notice.exception.NoticeErrorType;
import com.hankkiatti.domain.notice.exception.NoticeException;
import com.hankkiatti.domain.notice.repository.NoticeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 공지 (BE-71). 장애학생은 전체·장애학생, 도우미는 전체·도우미(아띠 소속이면 아띠까지) 공지를 본다.
 * 고정 먼저, 그다음 최신 게시순 20개씩.
 */
@Service
@RequiredArgsConstructor
public class NoticeService {

    public static final int PAGE_SIZE = 20;

    private final NoticeRepository noticeRepository;
    private final HelperRepository helperRepository;

    @Transactional(readOnly = true)
    public NoticesResponseDto getNotices(Long accountId, AccountRole role, int page) {
        if (page < 0) {
            throw new NoticeException(NoticeErrorType.INVALID_PAGE, "page=" + page);
        }
        Page<Notice> notices = noticeRepository.findPublishedByTargetIn(
                visibleTargets(accountId, role), PageRequest.of(page, PAGE_SIZE));
        return new NoticesResponseDto(
                notices.map(notice -> new NoticeResponseDto(notice.getId(), notice.getTitle(), notice.isPinned(),
                        notice.getPublishedAt())).getContent(),
                page, PAGE_SIZE, notices.getTotalElements(), notices.getTotalPages());
    }

    /**
     * 대상이 아닌 공지는 있어도 NOT_FOUND (있다는 것도, 삭제됐다는 것도 알리지 않는다). 볼 수 있던 공지가 삭제됐으면 DELETED.
     */
    @Transactional(readOnly = true)
    public NoticeDetailResponseDto getNotice(Long accountId, AccountRole role, Long noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .filter(found -> visibleTargets(accountId, role).contains(found.getTarget()))
                .orElseThrow(() -> new NoticeException(NoticeErrorType.NOT_FOUND, "noticeId=" + noticeId));
        if (notice.isDeleted()) {
            throw new NoticeException(NoticeErrorType.DELETED, "noticeId=" + noticeId);
        }
        return new NoticeDetailResponseDto(notice.getId(), notice.getTitle(), notice.getBody(), notice.getTarget(),
                notice.isPinned(), notice.getPublishedAt(), notice.getEditedAt());
    }

    // 아띠 소속은 지금 시점 기준 — 소속이 바뀌면 바로 보이거나 안 보인다
    private List<NoticeTarget> visibleTargets(Long accountId, AccountRole role) {
        boolean attiMember = role == AccountRole.HELPER
                && helperRepository.findById(accountId).map(Helper::isAttiMember).orElse(false);
        return NoticeTarget.visibleTo(role, attiMember);
    }
}
