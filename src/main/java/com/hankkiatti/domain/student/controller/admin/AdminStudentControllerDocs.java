package com.hankkiatti.domain.student.controller.admin;

import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.student.dto.request.AdminStudentCreateRequestDto;
import com.hankkiatti.domain.student.dto.request.AdminStudentUpdateRequestDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentAccountStatusResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCreateResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCredentialMailResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentDeactivationPreviewResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentInfoResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentsResponseDto;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "관리자 장애학생", description = "관리자 장애학생 관리 API")
public interface AdminStudentControllerDocs {

    @Operation(summary = "장애학생 목록", description = """
            이름·학번으로 검색하고 계정 상태로 필터링합니다. 이름·학번순 20명씩 페이지로 줍니다.
            전체 권한 관리자는 장애 유형 필터와 마스킹된 연락처를 함께 사용합니다.
            제한 권한 관리자에게는 이름·학번·상태·최근 신청만 제공하며 장애 유형 필터를 허용하지 않습니다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "제한 권한의 장애 유형 필터 요청")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "STUDENT_INVALID_PAGE — page가 0보다 작음")
    ResponseEntity<ApiResult<AdminStudentsResponseDto>> getStudents(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "이름·학번 검색어 (부분 일치)", example = "김한") String q,
            @Parameter(description = "장애 유형 필터 (전체 권한만)") DisabilityType disabilityType,
            @Parameter(description = "계정 상태 필터 (없으면 전체)", example = "ACTIVE") AccountStatus status,
            @Parameter(description = "페이지 (0부터, 기본 0)", example = "0") int page);

    @Operation(summary = "장애학생 정보 수정", description = "전체 권한 관리자가 이름·연락처·학교 이메일·장애 정보를 수정합니다. 학번은 바꾸지 않습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "전체 권한 관리자만 사용 가능")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "장애학생 없음")
    ResponseEntity<ApiResult<AdminStudentInfoResponseDto>> update(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "학생 계정 ID") Long studentAccountId,
            AdminStudentUpdateRequestDto request);

    @Operation(summary = "장애학생 계정정보 재발송", description = """
            전체 권한 관리자가 새 초기 비밀번호를 만들고 학교 이메일로 계정정보를 다시 보냅니다.
            등록 직후 발송 실패(FAILED)와 이미 받은(SENT) 장애학생 모두 이 API로 다시 보냅니다 — 학생이 바꿔 둔 비밀번호는 무효가 됩니다.
            기존 로그인 토큰과 비밀번호 재설정 링크도 무효가 되며, 메일 발송 중(PENDING)에는 중복 요청할 수 없습니다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발송 요청 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "전체 권한 관리자만 사용 가능")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "장애학생 없음")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "계정정보 메일 발송 중")
    ResponseEntity<ApiResult<AdminStudentCredentialMailResponseDto>> resendCredentialMail(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "학생 계정 ID") Long studentAccountId);

    @Operation(summary = "장애학생 계정 비활성화 미리보기", description = """
            비활성화 확인 모달에 표시할 진행 중 신청 수를 조회합니다. 식사가 이미 시작된 신청은 취소되지 않으므로 세지 않습니다.
            전체 권한 관리자만 사용할 수 있으며, 이미 비활성화된 계정이면 alreadyInactive가 true입니다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "전체 권한 관리자만 사용 가능")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "장애학생 없음")
    ResponseEntity<ApiResult<AdminStudentDeactivationPreviewResponseDto>> previewDeactivation(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "학생 계정 ID") Long studentAccountId);

    @Operation(summary = "장애학생 계정 비활성화", description = """
            전체 권한 관리자가 계정을 비활성화합니다. 기존 로그인 토큰과 비밀번호 재설정 링크를 무효화하고,
            진행 중인 신청과 해당 신청의 활성 지원을 함께 취소합니다. 식사가 이미 시작된 신청은 그대로 두어
            자동 처리(매칭 실패·이용 완료)를 따릅니다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "비활성화 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "전체 권한 관리자만 사용 가능")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "장애학생 없음")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 비활성화된 계정")
    ResponseEntity<ApiResult<AdminStudentAccountStatusResponseDto>> deactivate(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "학생 계정 ID") Long studentAccountId);

    @Operation(summary = "장애학생 계정 재활성화", description = """
            전체 권한 관리자가 비활성화된 장애학생 계정을 다시 활성화합니다.
            비활성화할 때 취소된 신청과 지원은 복원하지 않습니다.
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재활성화 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "전체 권한 관리자만 사용 가능")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "장애학생 없음")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 활성화된 계정")
    ResponseEntity<ApiResult<AdminStudentAccountStatusResponseDto>> activate(
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
}
