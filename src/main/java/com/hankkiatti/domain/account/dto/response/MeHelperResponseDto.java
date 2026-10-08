package com.hankkiatti.domain.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "도우미만 있는 내 정보")
public record MeHelperResponseDto(
        @Schema(description = "아띠 소속 여부") boolean attiMember,
        @Schema(description = "봉사시간 누적 (이용 완료 1건 1.0시간, 노쇼 0). 기록이 없으면 0.0", example = "3.0")
        BigDecimal volunteerHours
) {}
