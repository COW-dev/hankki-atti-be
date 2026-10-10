package com.hankkiatti.domain.student.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장애학생 상세에 보이는 도우미")
public record AdminStudentHelperResponseDto(
        @Schema(description = "도우미 계정 ID") Long id,
        @Schema(description = "이름", example = "한지우") String name
) {}
