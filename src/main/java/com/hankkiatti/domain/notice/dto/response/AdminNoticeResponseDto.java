package com.hankkiatti.domain.notice.dto.response;

import com.hankkiatti.domain.notice.entity.NoticeTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 공지 목록 한 줄")
public record AdminNoticeResponseDto(
        @Schema(description = "공지 ID") Long noticeId,
        @Schema(description = "제목", example = "10월 학식당 운영 시간 안내") String title,
        @Schema(description = "대상", example = "ALL") NoticeTarget target,
        @Schema(description = "목록 맨 위 고정") boolean pinned,
        @Schema(description = "게시 시각", example = "2026-10-01T09:00:00") LocalDateTime publishedAt,
        @Schema(description = "마지막 수정 시각 — 수정한 적 없으면 null (화면에 \"-\")", example = "2026-10-02T10:00:00") LocalDateTime editedAt
) {}
