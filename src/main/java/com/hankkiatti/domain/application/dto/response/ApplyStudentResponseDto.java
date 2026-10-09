package com.hankkiatti.domain.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

// 매칭된 도우미에게만 주는 장애학생 연락 정보 (요구사항 4.8). 전화번호·장애 유형은 없다
@Schema(description = "매칭된 장애학생")
public record ApplyStudentResponseDto(
        @Schema(description = "이름", example = "박서연") String name,
        @Schema(description = "카톡 ID", example = "seoyeon_p") String kakaoId
) {}
