package com.hankkiatti.domain.helprequest.dto.response;

import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.entity.RequestCancelType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "전체 신청 현황 한 줄 (전체 권한 — 장애 유형 포함)")
public record AdminHelpRequestResponseDto(
        @Schema(description = "신청 ID") Long id,
        @Schema(description = "식사 시작 시각", example = "2026-10-12T12:00:00") LocalDateTime startAt,
        @Schema(description = "끝 시각 (시작 + 1시간)", example = "2026-10-12T13:00:00") LocalDateTime endAt,
        @Schema(description = "신청 상태", example = "MATCHED") HelpRequestStatus status,
        @Schema(description = "필요한 도움 (배식·좌석·이동·기타 순)", example = "[\"SERVING\"]") List<HelpType> helpTypes,
        @Schema(description = "취소된 신청이면 취소 구분 (철회·학생 취소·계정 비활성화)", example = "STUDENT_WITHDRAW") RequestCancelType cancelType,
        @Schema(description = "장애학생 (장애 유형 포함)") AdminHelpRequestStudentResponseDto student,
        @Schema(description = "확정된 도우미. 매칭 완료·이용 완료·노쇼일 때만 있다") AdminHelpRequestHelperResponseDto helper,
        @Schema(description = "예비가 승격돼 도우미 응답을 기다리는 중 (그동안 helper는 없다)") boolean promotionPending,
        @Schema(description = "도우미가 바뀐 적 있음") boolean helperChanged,
        @Schema(description = "지금 예비 인원", example = "2") long waitingCount,
        @Schema(description = "신청 시각", example = "2026-10-10T20:41:00") LocalDateTime requestedAt,
        @Schema(description = "처음 매칭된 시각 (다시 매칭돼도 첫 시각). 매칭된 적 없으면 없다", example = "2026-10-10T20:55:00") LocalDateTime firstMatchedAt,
        @Schema(description = "신청 → 첫 매칭까지 걸린 분. 매칭된 적 없으면 없다", example = "14") Long minutesToMatch
) implements AdminHelpRequestRow {}
