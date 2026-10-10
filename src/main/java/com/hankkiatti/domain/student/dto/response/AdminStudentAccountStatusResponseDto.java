package com.hankkiatti.domain.student.dto.response;

import com.hankkiatti.domain.account.entity.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 장애학생 계정 상태 변경 응답")
public record AdminStudentAccountStatusResponseDto(
        @Schema(description = "학생 계정 ID") Long accountId,
        @Schema(description = "계정 상태", example = "INACTIVE") AccountStatus status,
        @Schema(description = "비활성화 시각") LocalDateTime deactivatedAt
) {}
