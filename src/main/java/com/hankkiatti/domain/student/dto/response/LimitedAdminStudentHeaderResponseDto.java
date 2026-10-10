package com.hankkiatti.domain.student.dto.response;

import com.hankkiatti.domain.account.entity.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장애학생 상세 프로필 (제한 권한 — 이름·학번·상태만)")
public record LimitedAdminStudentHeaderResponseDto(
        @Schema(description = "장애학생 계정 ID") Long id,
        @Schema(description = "이름", example = "김민지") String name,
        @Schema(description = "학번", example = "60231234") String studentNo,
        @Schema(description = "계정 상태", example = "ACTIVE") AccountStatus status
) implements AdminStudentHeader {}
