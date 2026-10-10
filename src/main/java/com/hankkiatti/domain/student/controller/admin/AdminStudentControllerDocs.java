package com.hankkiatti.domain.student.controller.admin;

import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.student.dto.request.AdminStudentCreateRequestDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCreateResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCredentialMailResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentDetailResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentSummaryResponseDto;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "관리자 장애학생", description = "관리자 장애학생 관리 API")
public interface AdminStudentControllerDocs {

    @Operation(summary = "장애학생 목록", description = """
            이름·학번으로 검색하고 계정 상태로 필터링합니다. 전체 권한 관리자는 장애 유형 필터와 마스킹된 연락처를 함께 사용합니다.
            제한 권한 관리자에게는 이름·학번·상태·최근 신청만 제공하며 장애 유형 필터를 허용하지 않습니다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "제한 권한의 장애 유형 필터 요청")
    ResponseEntity<ApiResult<List<AdminStudentSummaryResponseDto>>> getStudents(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "이름 또는 학번 검색어") String keyword,
            @Parameter(description = "장애 유형 필터 (전체 권한만)") DisabilityType disabilityType,
            @Parameter(description = "계정 상태 필터") AccountStatus status);

    @Operation(summary = "장애학생 상세", description = """
            정보·매칭현황·취소/노쇼 이력을 조회합니다. 전체 권한 관리자는 수정에 필요한 학생 정보 원문을 받습니다.
            제한 권한 관리자에게는 이름·학번·상태·최근 신청만 제공하며 민감한 학생 정보는 응답에서 제외합니다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "장애학생 없음")
    ResponseEntity<ApiResult<AdminStudentDetailResponseDto>> getStudent(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "학생 계정 ID") Long studentAccountId);

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
