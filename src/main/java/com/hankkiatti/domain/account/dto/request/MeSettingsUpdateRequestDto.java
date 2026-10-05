package com.hankkiatti.domain.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "내 설정 변경 요청")
public record MeSettingsUpdateRequestDto(

        @NotNull
        @Schema(description = "접근성 모드(큰 글씨 + 음성 읽기) 사용 여부", example = "true")
        Boolean accessibilityMode
) {}
