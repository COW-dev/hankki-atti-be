package com.hankkiatti.domain.student.dto.response;

import com.hankkiatti.domain.student.entity.CredentialMailStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장애학생 계정정보 메일 응답")
public record AdminStudentCredentialMailResponseDto(
        @Schema(description = "계정 ID") Long accountId,
        @Schema(description = "로그인 아이디. 학번과 같습니다.", example = "60261234") String loginId,
        @Schema(description = "학교 이메일", example = "student@mju.ac.kr") String schoolEmail,
        @Schema(description = "계정정보 메일 상태", example = "PENDING") CredentialMailStatus credentialMailStatus
) {}
