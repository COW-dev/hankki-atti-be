package com.hankkiatti.domain.helprequest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "전체 신청 현황 요약 숫자 (선택 기간 기준, 상태·검색과 상관없이)")
public record AdminHelpRequestSummaryResponseDto(
        @Schema(description = "신청 — 기간 안 모든 신청 (철회·취소 포함)", example = "12") long total,
        @Schema(description = "매칭 완료 — 도우미가 확정된 신청 (매칭 완료 + 이용 완료)", example = "8") long matched,
        @Schema(description = "모집 중", example = "2") long recruiting,
        @Schema(description = "매칭 실패", example = "1") long failed,
        @Schema(description = "노쇼", example = "1") long noShow
) {}
