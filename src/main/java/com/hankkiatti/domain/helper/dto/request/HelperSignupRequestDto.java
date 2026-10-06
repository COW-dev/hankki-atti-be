package com.hankkiatti.domain.helper.dto.request;

import com.hankkiatti.domain.auth.validation.Password;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "도우미 회원가입 요청")
public record HelperSignupRequestDto(

        @NotBlank
        @Size(max = 50)
        @Schema(description = "이름", example = "이도움")
        String name,

        @NotBlank
        @Pattern(regexp = "^\\d{8}$", message = "학번은 숫자 8자리입니다.")
        @Schema(description = "학번 (숫자 8자리)", example = "60231234")
        String studentNo,

        @NotBlank
        @Email
        @Size(max = 100)
        @Pattern(regexp = "(?i)^[^@\\s]+@mju\\.ac\\.kr$", message = "학교 이메일(@mju.ac.kr)로만 가입할 수 있습니다.")
        @Schema(description = "학교 이메일(@mju.ac.kr). 로그인 아이디이자 알림 메일 주소 (소문자로 저장)", example = "helper@mju.ac.kr")
        String email,

        @NotBlank
        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "휴대전화 번호 형식이 아닙니다.")
        @Schema(description = "휴대전화 번호. 센터만 본다 (긴급 연락용). 하이픈은 있어도 없어도 된다", example = "010-1234-5678")
        String phone,

        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^\\S+$", message = "카톡 ID에는 공백을 넣을 수 없습니다.")
        @Schema(description = "카카오톡 ID. 매칭된 학생에게만 공개, 공백 불가", example = "hankki_helper")
        String kakaoId,

        @NotBlank
        @Password
        @Schema(description = "비밀번호 (8~64자, 영문·숫자·특수문자 각 1개 이상)", example = "hankki!2026")
        String password,

        @Schema(description = "아띠 소속 여부 (본인 신고, 선택). 보내지 않으면 false", example = "false")
        Boolean attiMember,

        @NotNull
        @AssertTrue(message = "장애 유형별 안내 자료를 모두 확인해야 가입할 수 있습니다.")
        @Schema(description = "장애 유형별 안내 자료를 모두 확인했는지. true여야 가입된다", example = "true")
        Boolean guideConfirmed
) {}
