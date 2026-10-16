package com.hankkiatti.domain.student.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 장애학생 목록 한 페이지")
public record AdminStudentsResponseDto(
        @Schema(description = "장애학생 — 이름·학번순") List<AdminStudentSummaryResponseDto> students,
        @Schema(description = "지금 페이지 (0부터)", example = "0") int page,
        @Schema(description = "한 페이지 인원", example = "20") int size,
        @Schema(description = "조건에 맞는 전체 인원", example = "5") long totalCount,
        @Schema(description = "전체 페이지 수", example = "1") int totalPages
) {}
