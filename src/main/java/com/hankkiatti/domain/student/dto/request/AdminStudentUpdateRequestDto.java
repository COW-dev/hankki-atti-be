package com.hankkiatti.domain.student.dto.request;

import com.hankkiatti.domain.common.validation.KakaoId;
import com.hankkiatti.domain.common.validation.PhoneNumber;
import com.hankkiatti.domain.student.entity.DisabilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 장애학생 정보 수정 요청")
public record AdminStudentUpdateRequestDto(

        @NotBlank
        @Size(max = 50)
        @Schema(description = "이름", example = "김한끼")
        String name,

        @NotBlank
        @PhoneNumber
        @Schema(description = "전화번호", example = "010-1234-5678")
        String phone,

        @NotBlank
        @KakaoId
        @Schema(description = "카카오톡 ID", example = "hankki_student")
        String kakaoId,

        @NotBlank
        @Email
        @Size(max = 100)
        @Pattern(regexp = "(?i)^[^@\\s]+@mju\\.ac\\.kr$", message = "학교 이메일(@mju.ac.kr)을 입력해 주세요.")
        @Schema(description = "학교 이메일", example = "student@mju.ac.kr")
        String schoolEmail,

        @NotNull
        @Schema(description = "장애 유형", example = "PHYSICAL")
        DisabilityType disabilityType,

        @Size(max = 1000)
        @Schema(description = "특이사항", example = "식판 이동 도움이 필요합니다.")
        String specialNote
) {}
