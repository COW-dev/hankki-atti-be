package com.hankkiatti.domain.student.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.student.entity.DisabilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "관리자 장애학생 목록 항목")
public record AdminStudentSummaryResponseDto(
        @Schema(description = "학생 계정 ID") Long accountId,
        @Schema(description = "이름", example = "김한끼") String name,
        @Schema(description = "학번", example = "60261234") String studentNo,
        @Schema(description = "장애 유형. 전체 권한 관리자에게만 제공", example = "PHYSICAL")
        DisabilityType disabilityType,
        @Schema(description = "일부 가린 학교 이메일. 전체 권한 관리자에게만 제공", example = "st*****@mju.ac.kr")
        String schoolEmail,
        @Schema(description = "일부 가린 전화번호. 전체 권한 관리자에게만 제공", example = "010-****-5678")
        String phone,
        @Schema(description = "일부 가린 카카오톡 ID. 전체 권한 관리자에게만 제공", example = "ha**********")
        String kakaoId,
        @Schema(description = "계정 상태", example = "ACTIVE") AccountStatus status,
        @Schema(description = "가장 최근 신청의 식사 시작 시각. 신청이 없으면 null", example = "2026-10-12T12:00:00")
        LocalDateTime recentRequestAt
) {}
