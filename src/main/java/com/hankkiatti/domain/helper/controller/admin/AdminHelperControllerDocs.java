package com.hankkiatti.domain.helper.controller.admin;

import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.helper.dto.response.AdminHelperActivityResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperDetailResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelpersResponseDto;
import com.hankkiatti.global.response.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "관리자 - 도우미", description = "도우미 목록·상세·활동 이력 (조회 전용, 전체·제한 권한 같은 응답). 정보 수정은 도우미 본인만")
public interface AdminHelperControllerDocs {

    @Operation(summary = "도우미 목록", description = """
            이름·학번 검색(q), 계정 상태·아띠 소속 필터. 가입 최근순 20명씩, page는 0부터.
            전화번호는 가운데를 가려서 준다(maskedPhone) — 전체 번호는 상세에서.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "COMMON_INVALID_REQUEST — status·attiMember·page 형식이 틀림")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "HELPER_INVALID_PAGE — page가 0보다 작음")
    ResponseEntity<ApiResult<AdminHelpersResponseDto>> getHelpers(
            @Parameter(description = "이름·학번 검색어 (부분 일치)", example = "윤태") String q,
            @Parameter(description = "계정 상태 (없으면 전체)", example = "ACTIVE") AccountStatus status,
            @Parameter(description = "아띠 소속 (없으면 전체)", example = "true") Boolean attiMember,
            @Parameter(description = "페이지 (0부터, 기본 0)", example = "0") int page);

    @Operation(summary = "도우미 상세", description = """
            프로필(전체 전화번호 포함 — 긴급 연락용)과 활동 요약: 매칭·이용 완료·봉사시간 합계·취소·노쇼 건수.
            취소 건수에는 관리자 비활성화로 생긴 취소를 넣지 않는다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "HELPER_NOT_FOUND — 없는 도우미")
    ResponseEntity<ApiResult<AdminHelperDetailResponseDto>> getHelper(
            @Parameter(description = "도우미 계정 ID") Long helperId);

    @Operation(summary = "도우미 활동 이력", description = """
            매칭되거나 승격된 지원을 최근 식사부터: 매칭 완료·승격 응답 대기·승격 거절·도우미 취소·학생 취소·이용 완료·노쇼.
            예비로만 있다가 빠진 지원은 넣지 않는다. 도우미 취소는 사유·식사 몇 분 전·이후 처리(예비 승격이면 승격된 예비의 순번,
            모집 재개면 그 신청의 지금 상태), 노쇼는 이용 완료 몇 분 뒤 신고됐는지. 장애학생은 이름만 준다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "HELPER_NOT_FOUND — 없는 도우미")
    ResponseEntity<ApiResult<List<AdminHelperActivityResponseDto>>> getActivities(
            @Parameter(description = "도우미 계정 ID") Long helperId);
}
