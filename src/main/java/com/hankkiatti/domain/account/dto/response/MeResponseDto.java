package com.hankkiatti.domain.account.dto.response;

import com.hankkiatti.domain.account.entity.AccountRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인한 사용자의 계정 정보")
public record MeResponseDto(
        @Schema(description = "계정 ID") Long accountId,
        @Schema(description = "로그인 아이디", example = "60231234") String loginId,
        @Schema(description = "역할", example = "STUDENT") AccountRole role,
        @Schema(description = "접근성 모드(큰 글씨 + 음성 읽기) 사용 여부") boolean accessibilityMode
) {}
