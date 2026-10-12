package com.hankkiatti.domain.notice.controller.admin;

import com.hankkiatti.domain.notice.dto.request.AdminNoticeSaveRequestDto;
import com.hankkiatti.domain.notice.dto.response.AdminNoticeDeleteResponseDto;
import com.hankkiatti.domain.notice.dto.response.AdminNoticeDetailResponseDto;
import com.hankkiatti.domain.notice.dto.response.AdminNoticesResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "관리자 - 공지", description = "공지 목록·상세(모든 관리자)와 작성·수정·삭제(전체 권한만). 게시하면 대상에게 인앱 알림, 수정은 재알림 없음")
public interface AdminNoticeControllerDocs {

    @Operation(summary = "공지 목록", description = "삭제된 공지는 빼고, 고정 먼저 그다음 최신 게시순 20개씩. page는 0부터.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "NOTICE_INVALID_PAGE — page가 0보다 작음")
    ResponseEntity<ApiResult<AdminNoticesResponseDto>> getNotices(
            @Parameter(description = "페이지 (0부터, 기본 0)", example = "0") int page);

    @Operation(summary = "공지 상세", description = "수정 화면에 채울 내용 (본문 포함).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND — 없는 공지 / NOTICE_DELETED — 삭제된 공지")
    ResponseEntity<ApiResult<AdminNoticeDetailResponseDto>> getNotice(
            @Parameter(description = "공지 ID") Long noticeId);

    @Operation(summary = "공지 작성", description = """
            저장하면 바로 게시되고, 대상의 활성 장애학생·도우미에게 인앱 알림이 간다(메일·문자 없음).
            같은 관리자가 10초 안에 같은 제목으로 또 저장하면 막는다 — 저장 중에는 버튼도 막아 주세요.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "게시 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "COMMON_INVALID_REQUEST — 대상이 정해진 값(ALL·STUDENT·HELPER·ATTI)이 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아니거나 전체 권한이 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "NOTICE_DUPLICATE_SUBMIT — 방금 같은 제목으로 게시함")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "COMMON_VALIDATION_FAILED — 제목·본문·대상이 없거나 제목 100자·본문 5,000자 초과")
    ResponseEntity<ApiResult<AdminNoticeDetailResponseDto>> create(
            @Parameter(hidden = true) AuthPrincipal principal,
            AdminNoticeSaveRequestDto request);

    @Operation(summary = "공지 수정", description = "제목·본문·대상·고정을 모두 바꾼다. 알림은 다시 보내지 않는다(대상을 바꿔도). 수정 시각(editedAt)이 기록된다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "COMMON_INVALID_REQUEST — 대상이 정해진 값(ALL·STUDENT·HELPER·ATTI)이 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아니거나 전체 권한이 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND — 없는 공지 / NOTICE_DELETED — 삭제된 공지 (수정 중 다른 관리자가 지운 경우 포함)")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "COMMON_VALIDATION_FAILED — 제목·본문·대상이 없거나 제목 100자·본문 5,000자 초과")
    ResponseEntity<ApiResult<AdminNoticeDetailResponseDto>> update(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "공지 ID") Long noticeId,
            AdminNoticeSaveRequestDto request);

    @Operation(summary = "공지 삭제", description = """
            되돌릴 수 없다. 사용자·관리자 목록에서 바로 사라지고, 이미 받은 알림을 누르면 NOTICE_DELETED로 안내한다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "AUTH_ACCESS_DENIED — 관리자가 아니거나 전체 권한이 아님")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND — 없는 공지 / NOTICE_DELETED — 이미 삭제된 공지")
    ResponseEntity<ApiResult<AdminNoticeDeleteResponseDto>> delete(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "공지 ID") Long noticeId);
}
