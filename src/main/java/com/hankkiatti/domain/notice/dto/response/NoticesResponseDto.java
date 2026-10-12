package com.hankkiatti.domain.notice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "사용자 공지 목록 한 페이지")
public record NoticesResponseDto(
        @Schema(description = "내가 볼 수 있는 공지 — 고정 먼저, 그다음 최신 게시순") List<NoticeResponseDto> notices,
        @Schema(description = "지금 페이지 (0부터)", example = "0") int page,
        @Schema(description = "한 페이지 공지 수", example = "20") int size,
        @Schema(description = "볼 수 있는 전체 공지 수", example = "3") long totalCount,
        @Schema(description = "전체 페이지 수", example = "1") int totalPages
) {}
