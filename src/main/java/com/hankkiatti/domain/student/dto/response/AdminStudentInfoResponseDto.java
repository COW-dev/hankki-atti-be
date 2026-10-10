package com.hankkiatti.domain.student.dto.response;

import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.student.entity.CredentialMailStatus;
import com.hankkiatti.domain.student.entity.DisabilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "장애학생 상세 · 정보 탭 (전체 권한만)")
public record AdminStudentInfoResponseDto(
        @Schema(description = "장애학생 계정 ID") Long id,
        @Schema(description = "이름", example = "김민지") String name,
        @Schema(description = "학번", example = "60231234") String studentNo,
        @Schema(description = "계정 상태", example = "ACTIVE") AccountStatus status,
        @Schema(description = "장애 유형", example = "VISUAL") DisabilityType disabilityType,
        @Schema(description = "연락처", example = "010-1234-1234") String phone,
        @Schema(description = "학교 이메일", example = "60231234@mju.ac.kr") String schoolEmail,
        @Schema(description = "카톡 ID", example = "minji_k") String kakaoId,
        @Schema(description = "로그인 아이디 (학번)", example = "60231234") String loginId,
        @Schema(description = "접근성 모드 기본값 (큰 글씨 + 읽어 주기)") boolean accessibilityMode,
        @Schema(description = "등록일", example = "2026-09-20T10:00:00") LocalDateTime registeredAt,
        @Schema(description = "계정정보 메일 상태", example = "SENT") CredentialMailStatus credentialMailStatus,
        @Schema(description = "특이사항 메모. 없으면 없다", example = "식판 운반과 메뉴 읽어 주기가 필요해요.") String specialNote
) {}
