package com.hankkiatti.domain.notice.dto.response;

import com.hankkiatti.domain.notice.entity.NoticeTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "사용자 공지 상세. 작성자는 주지 않는다")
public record NoticeDetailResponseDto(
        @Schema(description = "공지 ID") Long noticeId,
        @Schema(description = "제목", example = "10월 학식당 운영 시간 안내") String title,
        @Schema(description = "본문 (일반 텍스트, 줄바꿈 포함). URL은 화면이 링크로 연결한다",
                example = "10월부터 학생식당 점심 운영 시간이 바뀌어요.") String body,
        @Schema(description = "대상", example = "ALL") NoticeTarget target,
        @Schema(description = "목록 맨 위 고정") boolean pinned,
        @Schema(description = "게시 시각", example = "2026-10-01T09:00:00") LocalDateTime publishedAt,
        @Schema(description = "마지막 수정 시각 — 수정한 적 없으면 null", example = "2026-10-02T10:00:00") LocalDateTime editedAt
) {}
