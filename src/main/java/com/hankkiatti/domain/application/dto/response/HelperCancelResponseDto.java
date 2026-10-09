package com.hankkiatti.domain.application.dto.response;

import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

// 취소한 도우미의 매칭 현황 카드에 바로 반영할 모양. 장애학생 정보와 승격 여부(예비가 있었는지)는 주지 않는다
@Schema(description = "취소된 지원")
public record HelperCancelResponseDto(
        @Schema(description = "지원 ID") Long applicationId,
        @Schema(description = "신청 ID") Long helpRequestId,
        @Schema(description = "지원 상태", example = "HELPER_CANCELED") ApplicationStatus status,
        @Schema(description = "식사 시작 시각", example = "2026-10-12T12:00:00") LocalDateTime startAt,
        @Schema(description = "끝 시각 (시작 + 1시간)", example = "2026-10-12T13:00:00") LocalDateTime endAt,
        @Schema(description = "필요한 도움 (배식·좌석·이동·기타 순)", example = "[\"SERVING\"]") List<HelpType> helpTypes,
        @Schema(description = "취소 사유", example = "ILLNESS") CancelReason cancelReason,
        @Schema(description = "취소 시각", example = "2026-10-11T18:00:00") LocalDateTime canceledAt
) {}
