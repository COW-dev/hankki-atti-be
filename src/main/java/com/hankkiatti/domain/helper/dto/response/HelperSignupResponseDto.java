package com.hankkiatti.domain.helper.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "도우미 회원가입 결과. 토큰은 주지 않으므로 로그인 화면으로 보낸다")
public record HelperSignupResponseDto(
        @Schema(description = "계정 ID") Long accountId,
        @Schema(description = "로그인 아이디 (소문자로 맞춘 이메일)", example = "helper@mju.ac.kr") String loginId
) {}
