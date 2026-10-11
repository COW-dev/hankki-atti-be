package com.hankkiatti.domain.notice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "삭제된 공지")
public record AdminNoticeDeleteResponseDto(
        @Schema(description = "공지 ID") Long noticeId,
        @Schema(description = "삭제 시각", example = "2026-10-03T15:00:00") LocalDateTime deletedAt
) {}
