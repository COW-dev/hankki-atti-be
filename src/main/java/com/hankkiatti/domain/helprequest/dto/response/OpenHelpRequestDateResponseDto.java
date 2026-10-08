package com.hankkiatti.domain.helprequest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "날짜와 그날 지원할 수 있는 신청")
public record OpenHelpRequestDateResponseDto(
        @Schema(description = "날짜", example = "2026-10-12") LocalDate date,
        @Schema(description = "그날 신청 (시작 시각 순)") List<OpenHelpRequestResponseDto> requests
) {}
