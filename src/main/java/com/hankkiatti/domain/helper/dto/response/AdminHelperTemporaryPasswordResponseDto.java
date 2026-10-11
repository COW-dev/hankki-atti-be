package com.hankkiatti.domain.helper.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "도우미 임시 비밀번호 발송 결과")
public record AdminHelperTemporaryPasswordResponseDto(
        @Schema(description = "도우미 계정 ID") Long helperId,
        @Schema(description = "임시 비밀번호를 보낸 가입 이메일", example = "yunth@mju.ac.kr") String email
) {}
