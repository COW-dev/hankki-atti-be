package com.hankkiatti.domain.helprequest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "신청할 수 있는 날짜와 그날 고를 수 있는 시작 시각")
public record TimeOptionDateResponseDto(
        @Schema(description = "날짜", example = "2026-10-12") LocalDate date,
        @Schema(description = "고를 수 있는 시작 시각 (시각 순)") List<TimeOptionResponseDto> startTimes
) {}
