package com.hankkiatti.domain.notice.controller.client;

import com.hankkiatti.domain.notice.dto.response.NoticeDetailResponseDto;
import com.hankkiatti.domain.notice.dto.response.NoticesResponseDto;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "공지", description = "내가 볼 수 있는 공지 — 장애학생: 전체·장애학생, 도우미: 전체·도우미(아띠 소속이면 아띠까지)")
public interface NoticeControllerDocs {

    @Operation(summary = "공지 목록", description = "고정 먼저 그다음 최신 게시순 20개씩. page는 0부터. 목록에는 본문을 주지 않는다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "NOTICE_INVALID_PAGE — page가 0보다 작음")
    ResponseEntity<ApiResult<NoticesResponseDto>> getNotices(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "페이지 (0부터, 기본 0)", example = "0") int page);

    @Operation(summary = "공지 상세", description = """
            알림을 눌러 들어올 수도 있다. 삭제된 공지면 NOTICE_DELETED("삭제된 공지예요" 안내),
            없거나 내 대상이 아닌 공지면 NOTICE_NOT_FOUND.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "NOTICE_NOT_FOUND — 없거나 볼 수 없는 공지 / NOTICE_DELETED — 삭제된 공지")
    ResponseEntity<ApiResult<NoticeDetailResponseDto>> getNotice(
            @Parameter(hidden = true) AuthPrincipal principal,
            @Parameter(description = "공지 ID") Long noticeId);
}
