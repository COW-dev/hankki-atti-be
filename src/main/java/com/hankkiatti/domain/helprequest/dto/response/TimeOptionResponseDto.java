package com.hankkiatti.domain.helprequest.dto.response;

import com.hankkiatti.domain.helprequest.entity.Meal;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "시작 시각 선택지 하나")
public record TimeOptionResponseDto(
        @Schema(description = "시작 시각", example = "2026-10-12T12:00:00") LocalDateTime startAt,
        @Schema(description = "끝 시각 (시작 + 1시간)", example = "2026-10-12T13:00:00") LocalDateTime endAt,
        @Schema(description = "점심(LUNCH)·저녁(DINNER) 구분", example = "LUNCH") Meal meal
) {}
