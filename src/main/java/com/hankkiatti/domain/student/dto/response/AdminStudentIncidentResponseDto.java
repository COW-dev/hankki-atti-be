package com.hankkiatti.domain.student.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hankkiatti.domain.application.entity.CancelReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "관리자 장애학생 취소·노쇼 이력")
public record AdminStudentIncidentResponseDto(
        @Schema(description = "이력 유형", example = "HELPER_CANCELED") AdminStudentIncidentType type,
        @Schema(description = "신청 ID") Long helpRequestId,
        @Schema(description = "지원 ID. 도우미 취소일 때만 제공") Long applicationId,
        @Schema(description = "식사 시작 시각") LocalDateTime startAt,
        @Schema(description = "발생 시각") LocalDateTime occurredAt,
        @Schema(description = "취소한 도우미 이름") String helperName,
        @Schema(description = "취소한 도우미 학번") String helperStudentNo,
        @Schema(description = "도우미 취소 사유") CancelReason cancelReason,
        @Schema(description = "도우미 기타 취소 사유 내용") String cancelReasonDetail
) {}
