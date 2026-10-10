package com.hankkiatti.domain.application.dto.response;

import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "내 지원 카드")
public record MyApplicationResponseDto(
        @Schema(description = "지원 ID") Long applicationId,
        @Schema(description = "신청 ID") Long helpRequestId,
        @Schema(description = "지원 상태", example = "MATCHED") ApplicationStatus status,
        @Schema(description = "식사 시작 시각", example = "2026-10-12T12:00:00") LocalDateTime startAt,
        @Schema(description = "끝 시각 (시작 + 1시간)", example = "2026-10-12T13:00:00") LocalDateTime endAt,
        @Schema(description = "필요한 도움 (배식·좌석·이동·기타 순)", example = "[\"SERVING\"]") List<HelpType> helpTypes,
        @Schema(description = "지금 예비 순번 (1부터). 예비일 때만 있다", example = "2") Integer waitingOrder,
        @Schema(description = "승격 응답 마감. 승격 응답 대기일 때만 있다 (식사 15분 전, 그보다 늦게 승격됐으면 식사 시작)", example = "2026-10-12T11:45:00") LocalDateTime promotionDeadline,
        @Schema(description = "장애학생 이름·카톡 ID. 매칭 완료일 때만 있다") ApplyStudentResponseDto student,
        @Schema(description = "취소 사유. 내가 취소한 지원(HELPER_CANCELED)일 때만 있다", example = "ILLNESS") CancelReason cancelReason,
        @Schema(description = "봉사시간. 이용 완료(1.0)·노쇼(0.0)일 때만 있다", example = "1.0") BigDecimal volunteerHours
) {}
