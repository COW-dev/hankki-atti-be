package com.hankkiatti.domain.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 문자 수신 동의 변경 요청")
public record MeSmsConsentUpdateRequestDto(

        @NotNull
        @Schema(description = "true면 동의, false면 수신 거부 (메일·앱 알림은 그대로 받는다)", example = "false")
        Boolean consent
) {}
