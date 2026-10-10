package com.hankkiatti.domain.student.controller.admin;

import com.hankkiatti.domain.student.dto.response.AdminStudentHelpRequestsResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentHistoriesResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentInfoResponseDto;
import com.hankkiatti.domain.student.service.AdminStudentDetailService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import com.hankkiatti.global.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 장애학생 상세 3탭 (조회). 등록·목록·수정은 AdminStudentController
@RestController
@RequestMapping("/api/admin/students")
@RequiredArgsConstructor
public class AdminStudentDetailController implements AdminStudentDetailControllerDocs {

    private final AdminStudentDetailService adminStudentDetailService;

    @Override
    @GetMapping("/{studentAccountId}")
    public ResponseEntity<ApiResult<AdminStudentInfoResponseDto>> getInfo(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long studentAccountId) {
        return ApiResponse.of(SuccessType.SUCCESS,
                adminStudentDetailService.getInfo(principal.accountId(), studentAccountId));
    }

    @Override
    @GetMapping("/{studentAccountId}/help-requests")
    public ResponseEntity<ApiResult<AdminStudentHelpRequestsResponseDto>> getHelpRequests(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long studentAccountId) {
        return ApiResponse.of(SuccessType.SUCCESS,
                adminStudentDetailService.getHelpRequests(principal.accountId(), studentAccountId));
    }

    @Override
    @GetMapping("/{studentAccountId}/histories")
    public ResponseEntity<ApiResult<AdminStudentHistoriesResponseDto>> getHistories(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long studentAccountId) {
        return ApiResponse.of(SuccessType.SUCCESS,
                adminStudentDetailService.getHistories(principal.accountId(), studentAccountId));
    }
}
