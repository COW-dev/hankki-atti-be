package com.hankkiatti.domain.student.dto.response;

import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "관리자 장애학생 신청별 매칭현황")
public record AdminStudentMatchingResponseDto(
        @Schema(description = "신청 ID") Long helpRequestId,
        @Schema(description = "식사 시작 시각") LocalDateTime startAt,
        @Schema(description = "식사 종료 시각") LocalDateTime endAt,
        @Schema(description = "신청 상태", example = "MATCHED") HelpRequestStatus status,
        @Schema(description = "필요한 도움") List<HelpType> helpTypes,
        @Schema(description = "이 신청의 지원·매칭 이력") List<AdminStudentApplicationResponseDto> applications
) {}
