package com.hankkiatti.domain.student.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장애학생 계정 비활성화 미리보기")
public record AdminStudentDeactivationPreviewResponseDto(
        @Schema(description = "학생 계정 ID") Long accountId,
        @Schema(description = "이미 비활성화된 계정인지 여부") boolean alreadyInactive,
        @Schema(description = "비활성화하면 취소될 진행 중 신청 수 (식사가 시작된 신청 제외)", example = "2") long activeRequestCount
) {}
