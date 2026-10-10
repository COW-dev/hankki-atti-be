package com.hankkiatti.domain.helprequest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "전체 신청 현황의 확정된 도우미")
public record AdminHelpRequestHelperResponseDto(
        @Schema(description = "도우미 계정 ID") Long id,
        @Schema(description = "이름", example = "한지우") String name
) {}
