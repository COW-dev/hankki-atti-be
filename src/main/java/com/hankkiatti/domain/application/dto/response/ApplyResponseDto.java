package com.hankkiatti.domain.application.dto.response;

import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "지원 결과: 바로 매칭 또는 예비 N번")
public record ApplyResponseDto(
        @Schema(description = "지원 ID") Long applicationId,
        @Schema(description = "신청 ID") Long helpRequestId,
        @Schema(description = "MATCHED 바로 매칭 · WAITING 예비", example = "MATCHED") ApplicationStatus status,
        @Schema(description = "식사 시작 시각", example = "2026-10-12T12:00:00") LocalDateTime startAt,
        @Schema(description = "끝 시각 (시작 + 1시간)", example = "2026-10-12T13:00:00") LocalDateTime endAt,
        @Schema(description = "필요한 도움 (배식·좌석·이동·기타 순)", example = "[\"SERVING\"]") List<HelpType> helpTypes,
        @Schema(description = "예비 순번 (1부터). 예비일 때만 있다", example = "2") Integer waitingOrder,
        @Schema(description = "장애학생 이름·카톡 ID. 바로 매칭일 때만 있다 (예비는 블라인드)") ApplyStudentResponseDto student
) {}
