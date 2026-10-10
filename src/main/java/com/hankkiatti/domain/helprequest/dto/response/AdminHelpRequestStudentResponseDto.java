package com.hankkiatti.domain.helprequest.dto.response;

import com.hankkiatti.domain.student.entity.DisabilityType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "전체 신청 현황의 장애학생 (전체 권한)")
public record AdminHelpRequestStudentResponseDto(
        @Schema(description = "장애학생 계정 ID (학생 상세로 이동할 때 쓴다)") Long id,
        @Schema(description = "이름", example = "김민지") String name,
        @Schema(description = "학번", example = "60231234") String studentNo,
        @Schema(description = "장애 유형", example = "VISUAL") DisabilityType disabilityType
) {}
