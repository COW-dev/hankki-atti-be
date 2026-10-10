package com.hankkiatti.domain.helprequest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "전체 신청 현황의 장애학생 (제한 권한 — 이름·학번만)")
public record LimitedAdminHelpRequestStudentResponseDto(
        @Schema(description = "장애학생 계정 ID (학생 상세로 이동할 때 쓴다)") Long id,
        @Schema(description = "이름", example = "김민지") String name,
        @Schema(description = "학번", example = "60231234") String studentNo
) {}
