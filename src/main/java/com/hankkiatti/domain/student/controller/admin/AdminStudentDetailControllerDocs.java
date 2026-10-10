package com.hankkiatti.domain.student.controller.admin;

import com.hankkiatti.domain.student.dto.response.AdminStudentHelpRequestsResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentHistoriesResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentInfoResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "관리자 - 장애학생 상세", description = "장애학생 상세 3탭 (정보 / 매칭현황 / 취소·노쇼 이력), 조회 전용")
public interface AdminStudentDetailControllerDocs {

    @Operation(summary = "장애학생 상세 · 정보", description = """
            이름·학번·상태·장애 유형·연락처·학교 이메일·카톡 ID·로그인 아이디·접근성 모드 기본값·등록일·계정정보 메일 상태·특이사항.
            전체 권한만 볼 수 있다 (제한 권한은 403 — 정보 탭이 없다).""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아니거나 제한 권한")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "STUDENT_NOT_FOUND — 없는 장애학생")
    ResponseEntity<ApiResult<AdminStudentInfoResponseDto>> getInfo(@Parameter(hidden = true) AuthPrincipal principal,
                                                                   @Parameter(description = "장애학생 계정 ID") Long studentAccountId);

    @Operation(summary = "장애학생 상세 · 매칭현황", description = """
            그 학생의 신청을 최근 식사부터. 확정 도우미, 예비에서 승격됐으면 승격 당시 예비 순번(promotedWaitingOrder), 승격 응답 대기, 도우미 바뀜,
            신청·첫 매칭 시각. 전체·제한 권한 모두 — 위 프로필(student)의 장애 유형은 전체 권한만 있다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "STUDENT_NOT_FOUND — 없는 장애학생")
    ResponseEntity<ApiResult<AdminStudentHelpRequestsResponseDto>> getHelpRequests(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "장애학생 계정 ID") Long studentAccountId);

    @Operation(summary = "장애학생 상세 · 취소·노쇼 이력", description = """
            도우미 취소(사유·기타 내용·식사 몇 분 전·이후 처리: 예비 승격이면 승격된 도우미와 순번, 모집 재개면 그 신청의 지금 상태),
            장애학생 매칭 취소(식사 몇 분 전), 노쇼 신고(이용 완료 몇 분 뒤·노쇼 처리된 도우미)를 최근 식사부터. 신청 철회는 넣지 않는다.
            패널티는 센터가 이 이력을 보고 사후에 판단한다. 전체·제한 권한 모두 — 위 프로필(student)의 장애 유형은 전체 권한만 있다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "STUDENT_NOT_FOUND — 없는 장애학생")
    ResponseEntity<ApiResult<AdminStudentHistoriesResponseDto>> getHistories(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "장애학생 계정 ID") Long studentAccountId);
}
