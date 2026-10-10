package com.hankkiatti.domain.student.dto.response;

import com.hankkiatti.domain.application.entity.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 장애학생 상세의 지원·매칭 이력")
public record AdminStudentApplicationResponseDto(
        @Schema(description = "지원 ID") Long applicationId,
        @Schema(description = "도우미 계정 ID") Long helperAccountId,
        @Schema(description = "도우미 이름") String helperName,
        @Schema(description = "도우미 학번") String helperStudentNo,
        @Schema(description = "지원 상태", example = "MATCHED") ApplicationStatus status,
        @Schema(description = "지원 시각") LocalDateTime appliedAt,
        @Schema(description = "매칭 확정 시각") LocalDateTime matchedAt,
        @Schema(description = "취소·철회 시각") LocalDateTime canceledAt
) {}
