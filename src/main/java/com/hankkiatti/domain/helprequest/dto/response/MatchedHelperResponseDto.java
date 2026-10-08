package com.hankkiatti.domain.helprequest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "매칭된 도우미. 이름과 카톡 ID만 공개한다 (전화번호는 비공개)")
public record MatchedHelperResponseDto(
        @Schema(description = "도우미 이름", example = "이도움") String name,
        @Schema(description = "카카오톡 ID", example = "dowoom_lee") String kakaoId
) {}
