package com.hankkiatti.domain.helper.controller.client;

import com.hankkiatti.domain.helper.dto.request.HelperSignupRequestDto;
import com.hankkiatti.domain.helper.dto.response.HelperSignupResponseDto;
import com.hankkiatti.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "도우미", description = "도우미 가입·정보")
public interface HelperControllerDocs {

    @Operation(summary = "도우미 회원가입 (로그인 불필요)",
            description = "장애 유형별 안내 자료를 모두 확인한 뒤(guideConfirmed=true) 가입한다. 승인 절차 없이 바로 로그인할 수 있다. "
                    + "학교 이메일(@mju.ac.kr)만 받고, 이메일이 로그인 아이디이며 소문자로 저장된다. 토큰은 주지 않으므로 가입 후 로그인 화면으로 보낸다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "가입 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
            description = "이미 가입된 이메일(HELPER_DUPLICATE_EMAIL)·학번(HELPER_DUPLICATE_STUDENT_NO), "
                    + "같은 값으로 동시에 가입 요청이 겹침(HELPER_SIGNUP_CONFLICT → 잠시 후 다시 시도)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422",
            description = "입력값 검증 실패 (학교 이메일 아님, 학번 8자리, 휴대전화 형식, 카톡 ID 공백, 비밀번호 규칙, 안내 자료 미확인 등)")
    ResponseEntity<ApiResult<HelperSignupResponseDto>> signup(HelperSignupRequestDto request);
}
