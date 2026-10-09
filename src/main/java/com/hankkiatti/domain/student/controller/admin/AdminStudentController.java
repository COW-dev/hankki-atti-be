package com.hankkiatti.domain.student.controller.admin;

import com.hankkiatti.domain.student.dto.request.AdminStudentCreateRequestDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCreateResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCredentialMailResponseDto;
import com.hankkiatti.domain.student.service.AdminStudentService;
import com.hankkiatti.global.response.ApiResponse;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.response.type.SuccessType;
import com.hankkiatti.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/students")
@RequiredArgsConstructor
public class AdminStudentController implements AdminStudentControllerDocs {

    private final AdminStudentService adminStudentService;

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
