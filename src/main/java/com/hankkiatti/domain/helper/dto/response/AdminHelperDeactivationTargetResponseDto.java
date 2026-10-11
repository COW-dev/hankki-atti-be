package com.hankkiatti.domain.helper.dto.response;

import com.hankkiatti.domain.application.entity.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "비활성화하면 해제될 지원 한 건")
public record AdminHelperDeactivationTargetResponseDto(
        @Schema(description = "지원 ID") Long applicationId,
        @Schema(description = "신청 ID") Long helpRequestId,
        @Schema(description = "식사 시작 시각", example = "2026-09-30T12:00:00") LocalDateTime startAt,
        @Schema(description = "장애학생 이름", example = "이서준") String studentName,
        @Schema(description = "지금 상태 — 매칭 완료·승격 응답 대기면 예비 승격 / 모집 재개, 예비면 자리에서 빠진다", example = "MATCHED") ApplicationStatus status,
        @Schema(description = "예비면 지금 순번", example = "2") Integer waitingOrder
) {}
