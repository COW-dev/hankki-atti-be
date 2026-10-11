package com.hankkiatti.domain.helper.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "도우미 비활성화 미리보기 — 확인 모달의 \"진행 중 매칭 N건, 예비 N건이 해제돼요\"")
public record AdminHelperDeactivationPreviewResponseDto(
        @Schema(description = "도우미 계정 ID") Long helperId,
        @Schema(description = "이미 비활성인지 — true면 targets는 그때 정리되지 못하고 남은 건이다 (비활성화를 다시 보내면 정리)") boolean alreadyInactive,
        @Schema(description = "해제될 매칭 건수 (매칭 완료·승격 응답 대기)", example = "1") long matchedCount,
        @Schema(description = "빠질 예비 건수", example = "1") long waitingCount,
        @Schema(description = "건별 — 식사 순") List<AdminHelperDeactivationTargetResponseDto> targets
) {}
