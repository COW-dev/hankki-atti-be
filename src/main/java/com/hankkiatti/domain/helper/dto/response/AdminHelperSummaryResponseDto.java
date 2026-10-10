package com.hankkiatti.domain.helper.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "도우미 활동 요약")
public record AdminHelperSummaryResponseDto(
        @Schema(description = "매칭된 건수 — 한 번이라도 매칭된 지원(이후 취소·이용 완료·노쇼 포함)", example = "5") long matchedCount,
        @Schema(description = "이용 완료 건수", example = "2") long completedCount,
        @Schema(description = "봉사시간 합계 (이용 완료 1.0, 노쇼 0)", example = "2.0") BigDecimal volunteerHours,
        @Schema(description = "도우미 취소 건수 — 관리자 비활성화로 생긴 취소는 넣지 않는다", example = "2") long canceledCount,
        @Schema(description = "노쇼 건수", example = "1") long noShowCount
) {}
