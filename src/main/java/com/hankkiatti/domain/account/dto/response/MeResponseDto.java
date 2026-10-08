package com.hankkiatti.domain.account.dto.response;

import com.hankkiatti.domain.account.entity.AccountRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인한 사용자의 내 정보 (마이페이지)")
public record MeResponseDto(
        @Schema(description = "계정 ID") Long accountId,
        @Schema(description = "로그인 아이디. 장애학생은 학번, 도우미는 이메일", example = "60231234") String loginId,
        @Schema(description = "역할", example = "STUDENT") AccountRole role,
        @Schema(description = "접근성 모드(큰 글씨 + 음성 읽기) 사용 여부") boolean accessibilityMode,
        @Schema(description = "이름", example = "김한끼") String name,
        @Schema(description = "학번", example = "60231234") String studentNo,
        @Schema(description = "휴대전화 번호", example = "010-1234-5678") String phone,
        @Schema(description = "카카오톡 ID", example = "hankki_kakao") String kakaoId,
        @Schema(description = "도우미만 있는 정보. 장애학생은 null") MeHelperResponseDto helper
) {}
