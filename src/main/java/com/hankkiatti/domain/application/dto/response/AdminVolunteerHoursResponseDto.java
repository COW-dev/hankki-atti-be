package com.hankkiatti.domain.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "봉사시간 수정 결과")
public record AdminVolunteerHoursResponseDto(
        @Schema(description = "지원 ID") Long applicationId,
        @Schema(description = "지금 봉사시간", example = "1.5") BigDecimal volunteerHours,
        @Schema(description = "방금 남긴 수정 이력") VolunteerHourChangeResponseDto change
) {}
