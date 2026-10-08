package com.hankkiatti.domain.account.dto.request;

import com.hankkiatti.domain.common.validation.KakaoId;
import com.hankkiatti.domain.common.validation.PhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "도우미 연락처 수정 요청")
public record MeContactUpdateRequestDto(

        @NotBlank
        @PhoneNumber
        @Schema(description = "휴대전화 번호. 센터만 본다 (긴급 연락용). 하이픈은 있어도 없어도 된다", example = "010-1234-5678")
        String phone,

        @NotBlank
        @KakaoId
        @Schema(description = "카카오톡 ID. 매칭된 장애학생에게만 공개, 공백 불가", example = "hankki_helper")
        String kakaoId
) {}
