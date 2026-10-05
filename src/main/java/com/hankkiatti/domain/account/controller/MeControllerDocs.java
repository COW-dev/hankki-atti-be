package com.hankkiatti.domain.account.controller;

import com.hankkiatti.domain.account.dto.response.MeResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "내 정보", description = "로그인한 장애학생·도우미의 계정 정보")
public interface MeControllerDocs {

    @Operation(summary = "내 계정 조회", description = "역할과 접근성 모드 설정을 확인한다. 비밀번호 변경이 필요한 계정은 403.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "비밀번호 변경 필요")
    ResponseEntity<ApiResult<MeResponseDto>> getMe(@Parameter(hidden = true) AuthPrincipal principal);
}
