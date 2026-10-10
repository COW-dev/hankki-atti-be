package com.hankkiatti.domain.student.controller.admin;

import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.student.dto.request.AdminStudentCreateRequestDto;
import com.hankkiatti.domain.student.dto.request.AdminStudentUpdateRequestDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentAccountStatusResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCreateResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCredentialMailResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentInfoResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentSummaryResponseDto;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.service.AdminStudentService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import com.hankkiatti.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/students")
@RequiredArgsConstructor
public class AdminStudentController implements AdminStudentControllerDocs {

    private final AdminStudentService adminStudentService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResult<List<AdminStudentSummaryResponseDto>>> getStudents(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DisabilityType disabilityType,
            @RequestParam(required = false) AccountStatus status) {
        return ApiResponse.of(SuccessType.SUCCESS,
                adminStudentService.getStudents(principal.accountId(), keyword, disabilityType, status));
    }

    @Override
    @PatchMapping("/{studentAccountId}")
    public ResponseEntity<ApiResult<AdminStudentInfoResponseDto>> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long studentAccountId,
            @Valid @RequestBody AdminStudentUpdateRequestDto request) {
        return ApiResponse.of(SuccessType.SUCCESS,
                adminStudentService.update(principal.accountId(), studentAccountId, request));
    }

    @Override
    @PostMapping("/{studentAccountId}/credential-mail/resend")
    public ResponseEntity<ApiResult<AdminStudentCredentialMailResponseDto>> resendCredentialMail(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long studentAccountId) {
        return ApiResponse.of(SuccessType.SUCCESS,
                adminStudentService.resendCredentialMail(principal.accountId(), studentAccountId));
    }

    @Override
    @PatchMapping("/{studentAccountId}/deactivate")
    public ResponseEntity<ApiResult<AdminStudentAccountStatusResponseDto>> deactivate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long studentAccountId) {
        return ApiResponse.of(SuccessType.SUCCESS,
                adminStudentService.deactivate(principal.accountId(), studentAccountId));
    }

    @Override
    @PostMapping
    public ResponseEntity<ApiResult<AdminStudentCreateResponseDto>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody AdminStudentCreateRequestDto request) {
        return ApiResponse.of(SuccessType.CREATED, adminStudentService.create(principal.accountId(), request));
    }

    @Override
    @PostMapping("/{studentAccountId}/credential-mail/retry")
    public ResponseEntity<ApiResult<AdminStudentCredentialMailResponseDto>> retryCredentialMail(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long studentAccountId) {
        return ApiResponse.of(SuccessType.SUCCESS,
                adminStudentService.retryCredentialMail(principal.accountId(), studentAccountId));
    }
}
