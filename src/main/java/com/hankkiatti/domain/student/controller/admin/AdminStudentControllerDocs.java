package com.hankkiatti.domain.student.controller.admin;

import com.hankkiatti.domain.student.dto.request.AdminStudentCreateRequestDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCreateResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCredentialMailResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "관리자 장애학생", description = "관리자 장애학생 관리 API")
public interface AdminStudentControllerDocs {

    @Operation(summary = "장애학생 등록", description = "전체 권한 관리자가 장애학생 계정을 생성하고 학교 이메일로 계정정보를 발송합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청값 오류")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "전체 권한 관리자만 사용 가능")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 등록된 학번")
    ResponseEntity<ApiResult<AdminStudentCreateResponseDto>> create(
            @Parameter(hidden = true) AuthPrincipal principal,
            AdminStudentCreateRequestDto request);

    @Operation(summary = "장애학생 계정정보 메일 다시 보내기", description = "전체 권한 관리자가 발송 실패 상태의 계정정보 메일을 새 임시 비밀번호로 다시 보냅니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발송 요청 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "전체 권한 관리자만 사용 가능")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "장애학생 없음")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "발송 실패 상태가 아님")
    ResponseEntity<ApiResult<AdminStudentCredentialMailResponseDto>> retryCredentialMail(
            @Parameter(hidden = true) AuthPrincipal principal,
            Long studentAccountId);
}
