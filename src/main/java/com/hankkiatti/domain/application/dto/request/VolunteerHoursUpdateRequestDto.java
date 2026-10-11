package com.hankkiatti.domain.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "봉사시간 수정 요청")
public record VolunteerHoursUpdateRequestDto(

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("10.0")
        @Schema(description = "새 봉사시간 — 0~10시간, 0.5시간 단위", example = "1.5")
        BigDecimal hours,

        @NotBlank
        @Size(max = 200)
        @Schema(description = "수정 사유 (필수)", example = "식사 보조가 30분 길어졌어요")
        String reason
) {}
