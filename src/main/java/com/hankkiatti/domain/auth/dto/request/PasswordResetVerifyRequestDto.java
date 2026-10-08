package com.hankkiatti.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비밀번호 재설정 링크 확인 요청")
public record PasswordResetVerifyRequestDto(

        @NotBlank
        @Schema(description = "메일 링크의 #token= 값", example = "q1w2e3r4t5y6u7i8o9p0a1s2d3f4g5h6j7k8l9z0x1c")
        String token
) {}
