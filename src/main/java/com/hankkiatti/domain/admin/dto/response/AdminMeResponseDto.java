package com.hankkiatti.domain.admin.dto.response;

import com.hankkiatti.domain.admin.entity.AdminGrade;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인한 관리자 정보")
public record AdminMeResponseDto(
        @Schema(description = "계정 ID") Long accountId,
        @Schema(description = "로그인 아이디", example = "center01") String loginId,
        @Schema(description = "이름", example = "김센터") String name,
        @Schema(description = "권한 등급 (사이드바 하단 표시, 메뉴 분기)", example = "FULL") AdminGrade grade
) {}
