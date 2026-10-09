package com.hankkiatti.domain.student.dto.response;

import com.hankkiatti.domain.student.entity.CredentialMailStatus;
import com.hankkiatti.domain.student.entity.DisabilityType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 장애학생 등록 응답")
public record AdminStudentCreateResponseDto(
        @Schema(description = "계정 ID") Long accountId,
        @Schema(description = "로그인 아이디. 학번과 같습니다.", example = "60261234") String loginId,
        @Schema(description = "이름", example = "김한끼") String name,
        @Schema(description = "학번", example = "60261234") String studentNo,
        @Schema(description = "학교 이메일", example = "student@mju.ac.kr") String schoolEmail,
        @Schema(description = "장애 유형", example = "PHYSICAL") DisabilityType disabilityType,
        @Schema(description = "계정정보 메일 상태", example = "PENDING") CredentialMailStatus credentialMailStatus
) {}
