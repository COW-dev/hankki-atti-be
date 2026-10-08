package com.hankkiatti.domain.helprequest.dto.response;

import com.hankkiatti.domain.application.entity.ApplyBlockReason;
import com.hankkiatti.domain.application.entity.ApplyOutcome;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

// 블라인드: 장애학생 정보는 물론 기타 도움 내용·메모도 넣지 않는다 (장애 관련 내용이 들어갈 수 있다)
@Schema(description = "지원할 수 있는 신청 카드 (블라인드)")
public record OpenHelpRequestResponseDto(
        @Schema(description = "신청 ID") Long id,
        @Schema(description = "식사 시작 시각", example = "2026-10-12T12:00:00") LocalDateTime startAt,
        @Schema(description = "끝 시각 (시작 + 1시간)", example = "2026-10-12T13:00:00") LocalDateTime endAt,
        @Schema(description = "필요한 도움 (배식·좌석·이동·기타 순)", example = "[\"SERVING\", \"SEATING\"]") List<HelpType> helpTypes,
        @Schema(description = "지금 지원하면: MATCH 바로 매칭 · WAITING 예비 등록 · BLOCKED 지원 불가", example = "MATCH") ApplyOutcome applyOutcome,
        @Schema(description = "지원 불가 이유. BLOCKED일 때만 있다", example = "TIME_OVERLAP") ApplyBlockReason blockReason
) {}
