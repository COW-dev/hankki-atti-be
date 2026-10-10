package com.hankkiatti.domain.student.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "장애학생 상세 · 취소·노쇼 이력 탭 (최근 식사부터)")
public record AdminStudentHistoriesResponseDto(
        @Schema(description = "화면 위 프로필 (전체 권한만 장애 유형)",
                oneOf = {AdminStudentHeaderResponseDto.class, LimitedAdminStudentHeaderResponseDto.class})
        AdminStudentHeader student,
        @Schema(description = "이력") List<AdminStudentHistoryResponseDto> histories
) {}
