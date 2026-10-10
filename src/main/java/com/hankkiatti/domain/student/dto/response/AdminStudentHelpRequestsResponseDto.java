package com.hankkiatti.domain.student.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "장애학생 상세 · 매칭현황 탭 (최근 식사부터)")
public record AdminStudentHelpRequestsResponseDto(
        @Schema(description = "화면 위 프로필 (전체 권한만 장애 유형)",
                oneOf = {AdminStudentHeaderResponseDto.class, LimitedAdminStudentHeaderResponseDto.class})
        AdminStudentHeader student,
        @Schema(description = "신청") List<AdminStudentHelpRequestResponseDto> helpRequests
) {}
