package com.hankkiatti.domain.student.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.auth.entity.TokenAudience;
import com.hankkiatti.domain.student.dto.response.AdminStudentAccountStatusResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCredentialMailResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentDeactivationPreviewResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentsResponseDto;
import com.hankkiatti.domain.student.entity.CredentialMailStatus;
import com.hankkiatti.domain.student.service.AdminStudentService;
import com.hankkiatti.global.response.ApiResult;
import com.hankkiatti.global.security.AuthPrincipal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminStudentControllerTest {

    private static final Long ADMIN_ACCOUNT_ID = 1L;
    private static final Long STUDENT_ACCOUNT_ID = 10L;
    private static final AuthPrincipal PRINCIPAL =
            new AuthPrincipal(ADMIN_ACCOUNT_ID, AccountRole.ADMIN, TokenAudience.ADMIN);

    @Mock
    private AdminStudentService adminStudentService;

    @InjectMocks
    private AdminStudentController adminStudentController;

    @Test
    void getStudents_검색어와페이지_서비스에그대로넘기고한페이지응답반환() {
        // given
        AdminStudentsResponseDto expected = new AdminStudentsResponseDto(List.of(), 2, 20, 0L, 0);
        given(adminStudentService.getStudents(ADMIN_ACCOUNT_ID, "김", null, AccountStatus.ACTIVE, 2))
                .willReturn(expected);

        // when
        ResponseEntity<ApiResult<AdminStudentsResponseDto>> response =
                adminStudentController.getStudents(PRINCIPAL, "김", null, AccountStatus.ACTIVE, 2);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(expected);
    }

    @Test
    void resendCredentialMail_관리자요청_재발송응답반환() {
        // given
        AdminStudentCredentialMailResponseDto expected = new AdminStudentCredentialMailResponseDto(
                STUDENT_ACCOUNT_ID, "60261234", "student@mju.ac.kr", CredentialMailStatus.PENDING);
        given(adminStudentService.resendCredentialMail(ADMIN_ACCOUNT_ID, STUDENT_ACCOUNT_ID)).willReturn(expected);

        // when
        ResponseEntity<ApiResult<AdminStudentCredentialMailResponseDto>> response =
                adminStudentController.resendCredentialMail(PRINCIPAL, STUDENT_ACCOUNT_ID);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(expected);
    }

    @Test
    void previewDeactivation_관리자요청_비활성화미리보기응답반환() {
        // given
        AdminStudentDeactivationPreviewResponseDto expected =
                new AdminStudentDeactivationPreviewResponseDto(STUDENT_ACCOUNT_ID, false, 2L);
        given(adminStudentService.previewDeactivation(ADMIN_ACCOUNT_ID, STUDENT_ACCOUNT_ID))
                .willReturn(expected);

        // when
        ResponseEntity<ApiResult<AdminStudentDeactivationPreviewResponseDto>> response =
                adminStudentController.previewDeactivation(PRINCIPAL, STUDENT_ACCOUNT_ID);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(expected);
        verify(adminStudentService).previewDeactivation(ADMIN_ACCOUNT_ID, STUDENT_ACCOUNT_ID);
    }

    @Test
    void activate_관리자요청_재활성화상태응답반환() {
        // given
        AdminStudentAccountStatusResponseDto expected = new AdminStudentAccountStatusResponseDto(
                STUDENT_ACCOUNT_ID, AccountStatus.ACTIVE, null);
        given(adminStudentService.activate(ADMIN_ACCOUNT_ID, STUDENT_ACCOUNT_ID)).willReturn(expected);

        // when
        ResponseEntity<ApiResult<AdminStudentAccountStatusResponseDto>> response =
                adminStudentController.activate(PRINCIPAL, STUDENT_ACCOUNT_ID);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data()).isEqualTo(expected);
        verify(adminStudentService).activate(ADMIN_ACCOUNT_ID, STUDENT_ACCOUNT_ID);
    }
}
