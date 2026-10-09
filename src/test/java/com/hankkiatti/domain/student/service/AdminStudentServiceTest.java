package com.hankkiatti.domain.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.entity.AdminGrade;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.service.MailOutboxService;
import com.hankkiatti.domain.student.dto.request.AdminStudentCreateRequestDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCreateResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCredentialMailResponseDto;
import com.hankkiatti.domain.student.entity.CredentialMailStatus;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.exception.StudentErrorType;
import com.hankkiatti.domain.student.exception.StudentException;
import com.hankkiatti.domain.student.repository.StudentRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminStudentServiceTest {

    private static final Long ADMIN_ID = 1L;

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private MailOutboxService mailOutboxService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminStudentService adminStudentService;

    @BeforeEach
    void setUp() {
        adminStudentService = new AdminStudentService(
                adminRepository, accountRepository, studentRepository, mailOutboxService, passwordEncoder);
    }

    private AdminStudentCreateRequestDto request() {
        return new AdminStudentCreateRequestDto(
                " 김한끼 ",
                "60261234",
                "01012345678",
                "hankki_student",
                "Student@MJU.ac.kr",
                DisabilityType.PHYSICAL,
                " 식판 이동 도움 ");
    }

    private Admin admin(AdminGrade grade) {
        Account account = new Account("admin", "hash", AccountRole.ADMIN, false, false);
        ReflectionTestUtils.setField(account, "id", ADMIN_ID);
        return new Admin(account, "관리자", grade);
    }

    private void givenFullAdmin() {
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin(AdminGrade.FULL)));
    }

    private void givenSavedAccountGetsId(long id) {
        given(accountRepository.saveAndFlush(any(Account.class))).willAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", id);
            return account;
        });
        given(studentRepository.saveAndFlush(any(Student.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    private Account studentAccount(Long id) {
        Account account = new Account("60261234", "old-hash", AccountRole.STUDENT, false, true);
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private Student failedMailStudent(Account account) {
        Student student = new Student(
                account,
                "김한끼",
                account.getLoginId(),
                "010-1234-5678",
                "hankki_student",
                "student@mju.ac.kr",
                DisabilityType.PHYSICAL,
                null);
        ReflectionTestUtils.setField(student, "accountId", account.getId());
        student.markCredentialMailFailed();
        return student;
    }

    @Test
    void create_전체권한관리자_학생계정과프로필을만들고계정정보메일을적재한다() {
        // given
        givenFullAdmin();
        given(passwordEncoder.encode(anyString())).willReturn("encoded-temporary-password");
        givenSavedAccountGetsId(10L);

        // when
        AdminStudentCreateResponseDto result = adminStudentService.create(ADMIN_ID, request());

        // then
        ArgumentCaptor<String> rawPassword = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(rawPassword.capture());
        assertThat(rawPassword.getValue()).hasSize(12);
        assertThat(rawPassword.getValue()).matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).*$");

        ArgumentCaptor<Account> account = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(account.capture());
        assertThat(account.getValue().getLoginId()).isEqualTo("60261234");
        assertThat(account.getValue().getPasswordHash()).isEqualTo("encoded-temporary-password");
        assertThat(account.getValue().getRole()).isEqualTo(AccountRole.STUDENT);
        assertThat(account.getValue().isMustChangePassword()).isTrue();
        assertThat(account.getValue().isAccessibilityMode()).isTrue();

        ArgumentCaptor<Student> student = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).saveAndFlush(student.capture());
        assertThat(student.getValue().getName()).isEqualTo("김한끼");
        assertThat(student.getValue().getStudentNo()).isEqualTo("60261234");
        assertThat(student.getValue().getPhone()).isEqualTo("010-1234-5678");
        assertThat(student.getValue().getSchoolEmail()).isEqualTo("student@mju.ac.kr");
        assertThat(student.getValue().getSpecialNote()).isEqualTo("식판 이동 도움");

        ArgumentCaptor<String> mailBody = ArgumentCaptor.forClass(String.class);
        verify(mailOutboxService).enqueue(
                eq(MailType.STUDENT_CREDENTIAL), eq("student@mju.ac.kr"), anyString(), mailBody.capture(), eq(10L));
        assertThat(mailBody.getValue()).contains("60261234", rawPassword.getValue());

        assertThat(result.accountId()).isEqualTo(10L);
        assertThat(result.loginId()).isEqualTo("60261234");
        assertThat(result.credentialMailStatus()).isEqualTo(CredentialMailStatus.PENDING);
    }

    @Test
    void create_제한권한관리자_ACCESS_DENIED() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin(AdminGrade.LIMITED)));

        // when & then
        assertThatThrownBy(() -> adminStudentService.create(ADMIN_ID, request()))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_이미등록된학번_DUPLICATE_STUDENT_NO() {
        // given
        givenFullAdmin();
        given(accountRepository.existsByLoginId("60261234")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> adminStudentService.create(ADMIN_ID, request()))
                .isInstanceOf(StudentException.class)
                .extracting("errorCode").isEqualTo(StudentErrorType.DUPLICATE_STUDENT_NO);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_동시등록으로유니크제약위반_REGISTRATION_CONFLICT() {
        // given
        givenFullAdmin();
        given(passwordEncoder.encode(anyString())).willReturn("encoded");
        given(accountRepository.saveAndFlush(any(Account.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate entry for key 'login_id'"));

        // when & then
        assertThatThrownBy(() -> adminStudentService.create(ADMIN_ID, request()))
                .isInstanceOf(StudentException.class)
                .extracting("errorCode").isEqualTo(StudentErrorType.REGISTRATION_CONFLICT);
        verify(mailOutboxService, never()).enqueue(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void retryCredentialMail_발송실패학생_새임시비밀번호를발급하고메일을다시적재한다() {
        // given
        givenFullAdmin();
        Account account = studentAccount(10L);
        Student student = failedMailStudent(account);
        given(accountRepository.findByIdForUpdate(10L)).willReturn(Optional.of(account));
        given(studentRepository.findByIdForUpdate(10L)).willReturn(Optional.of(student));
        given(passwordEncoder.encode(anyString())).willReturn("new-temporary-password-hash");

        // when
        AdminStudentCredentialMailResponseDto result = adminStudentService.retryCredentialMail(ADMIN_ID, 10L);

        // then
        assertThat(account.getPasswordHash()).isEqualTo("new-temporary-password-hash");
        assertThat(account.isMustChangePassword()).isTrue();
        assertThat(student.getCredentialMailStatus()).isEqualTo(CredentialMailStatus.PENDING);

        ArgumentCaptor<String> rawPassword = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(rawPassword.capture());
        ArgumentCaptor<String> mailBody = ArgumentCaptor.forClass(String.class);
        verify(mailOutboxService).enqueue(
                eq(MailType.STUDENT_CREDENTIAL), eq("student@mju.ac.kr"), anyString(), mailBody.capture(), eq(10L));
        assertThat(mailBody.getValue()).contains("60261234", rawPassword.getValue());

        assertThat(result.accountId()).isEqualTo(10L);
        assertThat(result.credentialMailStatus()).isEqualTo(CredentialMailStatus.PENDING);
    }

    @Test
    void retryCredentialMail_발송실패상태가아니면_CREDENTIAL_MAIL_NOT_FAILED() {
        // given
        givenFullAdmin();
        Account account = studentAccount(10L);
        Student student = new Student(
                account,
                "김한끼",
                "60261234",
                "010-1234-5678",
                "hankki_student",
                "student@mju.ac.kr",
                DisabilityType.PHYSICAL,
                null);
        given(accountRepository.findByIdForUpdate(10L)).willReturn(Optional.of(account));
        given(studentRepository.findByIdForUpdate(10L)).willReturn(Optional.of(student));

        // when & then
        assertThatThrownBy(() -> adminStudentService.retryCredentialMail(ADMIN_ID, 10L))
                .isInstanceOf(StudentException.class)
                .extracting("errorCode").isEqualTo(StudentErrorType.CREDENTIAL_MAIL_NOT_FAILED);
        verify(mailOutboxService, never()).enqueue(any(), anyString(), anyString(), anyString(), any());
    }
}
