package com.hankkiatti.domain.helper.dto.response;

import com.hankkiatti.domain.account.entity.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "도우미 비활성화 결과")
public record AdminHelperDeactivationResponseDto(
        @Schema(description = "도우미 계정 ID") Long helperId,
        @Schema(description = "계정 상태", example = "INACTIVE") AccountStatus status,
        @Schema(description = "비활성화 시각", example = "2026-10-11T10:00:00") LocalDateTime deactivatedAt,
        @Schema(description = "해제한 매칭 건수 (예비 승격 또는 모집 재개)", example = "1") int releasedMatchCount,
        @Schema(description = "빠진 예비 건수", example = "1") int releasedWaitingCount,
        @Schema(description = "처리하지 못하고 남은 건수 — 0보다 크면 비활성화를 다시 보내 정리한다", example = "0") int remainingCount
) {}
