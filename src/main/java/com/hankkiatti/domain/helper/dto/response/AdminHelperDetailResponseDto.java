package com.hankkiatti.domain.helper.dto.response;

import com.hankkiatti.domain.account.entity.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 도우미 상세 — 프로필과 활동 요약")
public record AdminHelperDetailResponseDto(
        @Schema(description = "도우미 계정 ID") Long helperId,
        @Schema(description = "이름", example = "윤태호") String name,
        @Schema(description = "학번", example = "20213333") String studentNo,
        @Schema(description = "계정 상태", example = "ACTIVE") AccountStatus status,
        @Schema(description = "아띠 소속 (가입 때 자기신고)") boolean attiMember,
        @Schema(description = "가입 이메일", example = "yunth@mju.ac.kr") String email,
        @Schema(description = "전화번호 (긴급 연락용)", example = "010-3333-3333") String phone,
        @Schema(description = "카톡 ID", example = "yunth") String kakaoId,
        @Schema(description = "가입 시각", example = "2026-09-11T10:00:00") LocalDateTime joinedAt,
        @Schema(description = "활동 요약") AdminHelperSummaryResponseDto summary
) {}
