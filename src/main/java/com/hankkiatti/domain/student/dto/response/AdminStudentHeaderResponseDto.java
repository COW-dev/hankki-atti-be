package com.hankkiatti.domain.student.dto.response;

import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.student.entity.DisabilityType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장애학생 상세 프로필 (전체 권한 — 장애 유형 포함)")
public record AdminStudentHeaderResponseDto(
        @Schema(description = "장애학생 계정 ID") Long id,
        @Schema(description = "이름", example = "김민지") String name,
        @Schema(description = "학번", example = "60231234") String studentNo,
        @Schema(description = "계정 상태", example = "ACTIVE") AccountStatus status,
        @Schema(description = "장애 유형", example = "VISUAL") DisabilityType disabilityType
) implements AdminStudentHeader {}
