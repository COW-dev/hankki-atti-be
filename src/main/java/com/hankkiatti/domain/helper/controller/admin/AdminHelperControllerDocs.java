package com.hankkiatti.domain.helper.controller.admin;

import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.helper.dto.response.AdminHelperAccountStatusResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperActivityResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperDeactivationPreviewResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperDeactivationResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperDetailResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelperTemporaryPasswordResponseDto;
import com.hankkiatti.domain.helper.dto.response.AdminHelpersResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Tag(name = "관리자 - 도우미", description = "도우미 목록·상세·활동 이력·임시 비밀번호 발송·비활성화 (전체·제한 권한 같은 응답). 정보 수정은 도우미 본인만")
public interface AdminHelperControllerDocs {

    @Operation(summary = "도우미 목록", description = """
            이름·학번 검색(q), 계정 상태·아띠 소속 필터. 가입 최근순 20명씩, page는 0부터.
            이메일·전화번호·카톡 ID는 일부를 가려서 준다(maskedEmail·maskedPhone·maskedKakaoId) — 전체는 상세에서.""")
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

    @Operation(summary = "도우미 임시 비밀번호 발송", description = """
            임시 비밀번호(12자)를 발급해 가입 이메일로 보낸다. 도우미는 다음 로그인 때 비밀번호를 바꿔야 한다(mustChangePassword).
            기존 로그인은 모두 끊기고, 안 쓴 비밀번호 재설정 링크도 무효가 된다. 응답의 email은 보낸 주소다.
            메일은 아웃박스로 보내며 발송 결과는 저장하지 않는다 — 받지 못했다면 다시 보낸다(다시 보내면 앞의 임시 비밀번호는 무효).""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발송 요청 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "HELPER_NOT_FOUND — 없는 도우미")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "HELPER_INACTIVE — 비활성화된 도우미 (다시 활성화한 뒤 보낸다)")
    ResponseEntity<ApiResult<AdminHelperTemporaryPasswordResponseDto>> issueTemporaryPassword(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "도우미 계정 ID") Long helperId);

    @Operation(summary = "도우미 비활성화 미리보기", description = """
            비활성화하면 해제될 진행 중 매칭(매칭 완료·승격 응답 대기)·예비 건수와 건별 식사 일시·장애학생 이름·상태·예비 순번.
            식사가 이미 시작된 매칭은 해제하지 않아 넣지 않는다. 이미 비활성이면 alreadyInactive = true이고 targets는 정리되지 못하고 남은 건이다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "HELPER_NOT_FOUND — 없는 도우미")
    ResponseEntity<ApiResult<AdminHelperDeactivationPreviewResponseDto>> previewDeactivation(
            @Parameter(description = "도우미 계정 ID") Long helperId);

    @Operation(summary = "도우미 비활성화", description = """
            로그인을 막고(기존 로그인도 바로 끊긴다), 식사 전 진행 중 매칭은 도우미 취소처럼 처리한다 — 사유 "관리자 비활성화"(패널티 이력 아님),
            예비가 있으면 1번 승격, 없으면 모집 재개(장애학생에게 도우미 변경·다시 모집 알림). 예비 자리는 빠진다.
            건마다 따로 처리해 remainingCount가 0보다 크면 같은 요청을 다시 보내 남은 건을 정리한다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "비활성화 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "HELPER_NOT_FOUND — 없는 도우미")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "HELPER_ALREADY_INACTIVE — 이미 비활성이고 정리할 건도 없음")
    ResponseEntity<ApiResult<AdminHelperDeactivationResponseDto>> deactivate(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "도우미 계정 ID") Long helperId);

    @Operation(summary = "도우미 재활성화", description = """
            다시 로그인할 수 있게 한다. 비활성화 때 해제된 매칭·예비는 되돌리지 않는다 (이미 승격·모집 재개됐다).""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재활성화 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "HELPER_NOT_FOUND — 없는 도우미")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "HELPER_ALREADY_ACTIVE — 이미 활성")
    ResponseEntity<ApiResult<AdminHelperAccountStatusResponseDto>> activate(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "도우미 계정 ID") Long helperId);
}
