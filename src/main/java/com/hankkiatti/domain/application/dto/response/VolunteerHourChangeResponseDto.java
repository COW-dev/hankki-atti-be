package com.hankkiatti.domain.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "봉사시간 수정 이력 한 건 — \"수정됨 · 1시간 → 1.5시간 · 관리자 · 일시\"")
public record VolunteerHourChangeResponseDto(
        @Schema(description = "이력 ID") Long changeId,
        @Schema(description = "수정 전 봉사시간", example = "1.0") BigDecimal beforeHours,
        @Schema(description = "수정 후 봉사시간", example = "1.5") BigDecimal afterHours,
        @Schema(description = "수정 사유", example = "식사 보조가 30분 길어졌어요") String reason,
        @Schema(description = "수정한 관리자 이름", example = "김센터") String adminName,
        @Schema(description = "수정 시각", example = "2026-10-11T15:00:00") LocalDateTime changedAt
) {}
