package com.hankkiatti.domain.student.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.student.entity.CredentialMailStatus;
import com.hankkiatti.domain.student.entity.DisabilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "관리자 장애학생 상세 정보")
public record AdminStudentInfoResponseDto(
        @Schema(description = "학생 계정 ID") Long accountId,
        @Schema(description = "이름", example = "김한끼") String name,
        @Schema(description = "학번", example = "60261234") String studentNo,
        @Schema(description = "전화번호. 전체 권한 관리자에게만 원문 제공", example = "010-1234-5678") String phone,
        @Schema(description = "카카오톡 ID. 전체 권한 관리자에게만 제공", example = "hankki_student") String kakaoId,
        @Schema(description = "학교 이메일. 전체 권한 관리자에게만 원문 제공", example = "student@mju.ac.kr")
        String schoolEmail,
        @Schema(description = "장애 유형. 전체 권한 관리자에게만 제공", example = "PHYSICAL")
        DisabilityType disabilityType,
        @Schema(description = "특이사항. 전체 권한 관리자에게만 제공") String specialNote,
        @Schema(description = "계정 상태", example = "ACTIVE") AccountStatus status,
        @Schema(description = "마지막 로그인 시각. 전체 권한 관리자에게만 제공") LocalDateTime lastLoginAt,
        @Schema(description = "계정 비활성화 시각. 전체 권한 관리자에게만 제공") LocalDateTime deactivatedAt,
        @Schema(description = "계정정보 메일 상태. 전체 권한 관리자에게만 제공", example = "SENT")
        CredentialMailStatus credentialMailStatus,
        @Schema(description = "계정정보 메일 발송 시각. 전체 권한 관리자에게만 제공")
        LocalDateTime credentialMailSentAt,
        @Schema(description = "가장 최근 신청의 식사 시작 시각") LocalDateTime recentRequestAt
) {}
