package com.hankkiatti.domain.student.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 장애학생 상세")
public record AdminStudentDetailResponseDto(
        @Schema(description = "정보 탭") AdminStudentInfoResponseDto information,
        @Schema(description = "매칭현황 탭. 식사 시작 시각 최근 순")
        List<AdminStudentMatchingResponseDto> matchingHistory,
        @Schema(description = "취소·노쇼 이력 탭. 발생 시각 최근 순")
        List<AdminStudentIncidentResponseDto> incidentHistory
) {}
