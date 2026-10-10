package com.hankkiatti.domain.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.admin.entity.Admin;
import com.hankkiatti.domain.admin.entity.AdminGrade;
import com.hankkiatti.domain.admin.repository.AdminRepository;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.entity.CancelReason;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.auth.repository.PasswordResetTokenRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.service.MailOutboxService;
import com.hankkiatti.domain.student.dto.request.AdminStudentCreateRequestDto;
import com.hankkiatti.domain.student.dto.request.AdminStudentUpdateRequestDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentAccountStatusResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCreateResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentCredentialMailResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentDetailResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentIncidentType;
import com.hankkiatti.domain.student.dto.response.AdminStudentInfoResponseDto;
import com.hankkiatti.domain.student.dto.response.AdminStudentSummaryResponseDto;
import com.hankkiatti.domain.student.entity.CredentialMailStatus;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.exception.StudentErrorType;
import com.hankkiatti.domain.student.exception.StudentException;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.domain.student.repository.StudentRecentRequestProjection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminStudentServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 10, 0);

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private MailOutboxService mailOutboxService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private Clock clock;
    private AdminStudentService adminStudentService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-05T01:00:00Z"), ZoneId.of("Asia/Seoul"));
        adminStudentService = new AdminStudentService(
                adminRepository, accountRepository, studentRepository, helpRequestRepository,
                applicationRepository, passwordResetTokenRepository, mailOutboxService, passwordEncoder, clock);
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

    private Student student(Account account) {
        Student student = new Student(
                account,
                "김한끼",
                account.getLoginId(),
                "010-1234-5678",
                "hankki_student",
                "student@mju.ac.kr",
                DisabilityType.PHYSICAL,
                "식판 이동 도움");
        ReflectionTestUtils.setField(student, "accountId", account.getId());
        return student;
    }

    private Helper helper(Long id) {
        Account account = new Account("helper@mju.ac.kr", "hash", AccountRole.HELPER, false, false);
        ReflectionTestUtils.setField(account, "id", id);
        Helper helper = new Helper(account, "이도우미", "60260001", "helper@mju.ac.kr",
                "010-0000-0000", "helper_kakao", false, LocalDateTime.of(2026, 10, 1, 9, 0));
        ReflectionTestUtils.setField(helper, "accountId", id);
        return helper;
    }

    private HelpRequest helpRequest(Student student, long id, LocalDateTime startAt) {
        HelpRequest request = new HelpRequest(student, startAt, Set.of(HelpType.SERVING), null, null);
        ReflectionTestUtils.setField(request, "id", id);
        return request;
    }

    @Test
    void getStudents_전체권한관리자_민감정보를가려서최근신청과함께조회한다() {
        // given
        givenFullAdmin();
        Account account = studentAccount(10L);
        Student student = student(account);
        LocalDateTime recentRequestAt = LocalDateTime.of(2026, 10, 12, 12, 0);
        StudentRecentRequestProjection projection = mock(StudentRecentRequestProjection.class);
        given(projection.getStudentAccountId()).willReturn(10L);
        given(projection.getRecentRequestAt()).willReturn(recentRequestAt);
        given(studentRepository.search("김", DisabilityType.PHYSICAL, AccountStatus.ACTIVE))
                .willReturn(List.of(student));
        given(helpRequestRepository.findRecentRequestAtByStudentIds(List.of(10L)))
                .willReturn(List.of(projection));

        // when
        List<AdminStudentSummaryResponseDto> result = adminStudentService.getStudents(
                ADMIN_ID, " 김 ", DisabilityType.PHYSICAL, AccountStatus.ACTIVE);

        // then
        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.accountId()).isEqualTo(10L);
            assertThat(item.name()).isEqualTo("김한끼");
            assertThat(item.disabilityType()).isEqualTo(DisabilityType.PHYSICAL);
            assertThat(item.schoolEmail()).isEqualTo("st*****@mju.ac.kr");
            assertThat(item.phone()).isEqualTo("010-****-5678");
            assertThat(item.kakaoId()).isEqualTo("ha************");
            assertThat(item.status()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(item.recentRequestAt()).isEqualTo(recentRequestAt);
        });
    }

    @Test
    void getStudents_제한권한관리자_민감정보없이조회한다() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin(AdminGrade.LIMITED)));
        Student student = student(studentAccount(10L));
        given(studentRepository.search(null, null, null)).willReturn(List.of(student));
        given(helpRequestRepository.findRecentRequestAtByStudentIds(List.of(10L))).willReturn(List.of());

        // when
        AdminStudentSummaryResponseDto result = adminStudentService.getStudents(ADMIN_ID, null, null, null).get(0);

        // then
        assertThat(result.name()).isEqualTo("김한끼");
        assertThat(result.studentNo()).isEqualTo("60261234");
        assertThat(result.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.recentRequestAt()).isNull();
        assertThat(result.disabilityType()).isNull();
        assertThat(result.schoolEmail()).isNull();
        assertThat(result.phone()).isNull();
        assertThat(result.kakaoId()).isNull();
    }

    @Test
    void getStudents_제한권한관리자의장애유형필터_ACCESS_DENIED() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin(AdminGrade.LIMITED)));

        // when & then
        assertThatThrownBy(() -> adminStudentService.getStudents(
                ADMIN_ID, null, DisabilityType.VISUAL, null))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
        verify(studentRepository, never()).search(any(), any(), any());
    }

    @Test
    void getStudent_전체권한관리자_원본정보와매칭현황과취소노쇼이력을조회한다() {
        // given
        givenFullAdmin();
        Student student = student(studentAccount(10L));
        Helper helper = helper(20L);
        LocalDateTime now = LocalDateTime.of(2026, 10, 5, 10, 0);

        HelpRequest withdrawn = helpRequest(student, 101L, now.plusDays(1));
        withdrawn.withdraw(now.plusMinutes(1));
        HelpRequest studentCanceled = helpRequest(student, 102L, now.plusDays(2));
        studentCanceled.match(now);
        studentCanceled.cancelByStudent(now.plusMinutes(2));
        HelpRequest deactivated = helpRequest(student, 103L, now.plusDays(3));
        deactivated.cancelByDeactivation(now.plusMinutes(3));
        HelpRequest noShow = helpRequest(student, 104L, now.plusDays(4));
        noShow.match(now);
        noShow.complete(now.plusHours(1));
        noShow.reportNoShow(now.plusHours(2));
        HelpRequest helperCanceled = helpRequest(student, 105L, now.plusDays(5));
        helperCanceled.match(now);
        Application application = new Application(helperCanceled, helper, now);
        ReflectionTestUtils.setField(application, "id", 201L);
        application.match(now);
        application.cancelByHelper(CancelReason.OTHER, "개인 사정", now.plusHours(3));

        List<HelpRequest> requests = List.of(helperCanceled, noShow, deactivated, studentCanceled, withdrawn);
        given(studentRepository.findWithAccountByAccountId(10L)).willReturn(Optional.of(student));
        given(helpRequestRepository.findByStudentAccountIdOrderByStartAtDescIdDesc(10L)).willReturn(requests);
        given(applicationRepository.findWithHelperByHelpRequestIdIn(any())).willReturn(List.of(application));

        // when
        AdminStudentDetailResponseDto result = adminStudentService.getStudent(ADMIN_ID, 10L);

        // then
        assertThat(result.information().phone()).isEqualTo("010-1234-5678");
        assertThat(result.information().schoolEmail()).isEqualTo("student@mju.ac.kr");
        assertThat(result.information().disabilityType()).isEqualTo(DisabilityType.PHYSICAL);
        assertThat(result.information().recentRequestAt()).isEqualTo(helperCanceled.getStartAt());
        assertThat(result.matchingHistory()).hasSize(5);
        assertThat(result.matchingHistory().get(0).applications()).singleElement().satisfies(item -> {
            assertThat(item.applicationId()).isEqualTo(201L);
            assertThat(item.helperName()).isEqualTo("이도우미");
        });
        assertThat(result.incidentHistory()).extracting("type").containsExactly(
                AdminStudentIncidentType.HELPER_CANCELED,
                AdminStudentIncidentType.NO_SHOW,
                AdminStudentIncidentType.ACCOUNT_DEACTIVATED,
                AdminStudentIncidentType.STUDENT_CANCELED,
                AdminStudentIncidentType.REQUEST_WITHDRAWN);
        assertThat(result.incidentHistory().get(0).cancelReason()).isEqualTo(CancelReason.OTHER);
        assertThat(result.incidentHistory().get(0).cancelReasonDetail()).isEqualTo("개인 사정");
    }

    @Test
    void getStudent_제한권한관리자_민감한정보를제외한다() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin(AdminGrade.LIMITED)));
        Student student = student(studentAccount(10L));
        given(studentRepository.findWithAccountByAccountId(10L)).willReturn(Optional.of(student));
        given(helpRequestRepository.findByStudentAccountIdOrderByStartAtDescIdDesc(10L)).willReturn(List.of());

        // when
        AdminStudentDetailResponseDto result = adminStudentService.getStudent(ADMIN_ID, 10L);

        // then
        assertThat(result.information().name()).isEqualTo("김한끼");
        assertThat(result.information().studentNo()).isEqualTo("60261234");
        assertThat(result.information().status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.information().phone()).isNull();
        assertThat(result.information().kakaoId()).isNull();
        assertThat(result.information().schoolEmail()).isNull();
        assertThat(result.information().disabilityType()).isNull();
        assertThat(result.information().specialNote()).isNull();
        assertThat(result.information().credentialMailStatus()).isNull();
        assertThat(result.matchingHistory()).isEmpty();
        assertThat(result.incidentHistory()).isEmpty();
        verify(applicationRepository, never()).findWithHelperByHelpRequestIdIn(any());
    }

    @Test
    void getStudent_없는학생_NOT_FOUND() {
        // given
        givenFullAdmin();
        given(studentRepository.findWithAccountByAccountId(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminStudentService.getStudent(ADMIN_ID, 99L))
                .isInstanceOf(StudentException.class)
                .extracting("errorCode").isEqualTo(StudentErrorType.NOT_FOUND);
    }

    @Test
    void update_전체권한관리자_학생정보를정규화해수정한다() {
        // given
        givenFullAdmin();
        Student student = student(studentAccount(10L));
        AdminStudentUpdateRequestDto request = new AdminStudentUpdateRequestDto(
                " 박수정 ",
                "01098765432",
                " revised_kakao ",
                "Revised@MJU.ac.kr ",
                DisabilityType.HEARING,
                " 보청기 사용 ");
        given(studentRepository.findByIdForUpdate(10L)).willReturn(Optional.of(student));
        given(helpRequestRepository.findRecentRequestAtByStudentIds(List.of(10L))).willReturn(List.of());

        // when
        AdminStudentInfoResponseDto result = adminStudentService.update(ADMIN_ID, 10L, request);

        // then
        assertThat(student.getName()).isEqualTo("박수정");
        assertThat(student.getPhone()).isEqualTo("010-9876-5432");
        assertThat(student.getKakaoId()).isEqualTo("revised_kakao");
        assertThat(student.getSchoolEmail()).isEqualTo("revised@mju.ac.kr");
        assertThat(student.getDisabilityType()).isEqualTo(DisabilityType.HEARING);
        assertThat(student.getSpecialNote()).isEqualTo("보청기 사용");
        assertThat(result.name()).isEqualTo("박수정");
        assertThat(result.phone()).isEqualTo("010-9876-5432");
    }

    @Test
    void update_제한권한관리자_ACCESS_DENIED() {
        // given
        given(adminRepository.findById(ADMIN_ID)).willReturn(Optional.of(admin(AdminGrade.LIMITED)));
        AdminStudentUpdateRequestDto request = new AdminStudentUpdateRequestDto(
                "박수정", "010-9876-5432", "revised", "revised@mju.ac.kr", DisabilityType.HEARING, null);

        // when & then
        assertThatThrownBy(() -> adminStudentService.update(ADMIN_ID, 10L, request))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.ACCESS_DENIED);
        verify(studentRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void resendCredentialMail_발송완료학생_초기비밀번호를재생성하고재설정링크를무효화한다() {
        // given
        givenFullAdmin();
        Account account = studentAccount(10L);
        Student student = student(account);
        student.markCredentialMailSent(NOW.minusDays(1));
        given(accountRepository.findByIdForUpdate(10L)).willReturn(Optional.of(account));
        given(studentRepository.findByIdForUpdate(10L)).willReturn(Optional.of(student));
        given(passwordEncoder.encode(anyString())).willReturn("new-temporary-password-hash");

        // when
        AdminStudentCredentialMailResponseDto result =
                adminStudentService.resendCredentialMail(ADMIN_ID, 10L);

        // then
        assertThat(account.getPasswordHash()).isEqualTo("new-temporary-password-hash");
        assertThat(account.isMustChangePassword()).isTrue();
        assertThat(account.getTokenVersion()).isEqualTo(1);
        assertThat(student.getCredentialMailStatus()).isEqualTo(CredentialMailStatus.PENDING);
        verify(passwordResetTokenRepository).invalidateAllByAccountId(10L, NOW);
        verify(mailOutboxService).enqueue(
                eq(MailType.STUDENT_CREDENTIAL), eq("student@mju.ac.kr"), anyString(), anyString(), eq(10L));
        assertThat(result.credentialMailStatus()).isEqualTo(CredentialMailStatus.PENDING);
    }

    @Test
    void resendCredentialMail_발송중학생_CREDENTIAL_MAIL_PENDING() {
        // given
        givenFullAdmin();
        Account account = studentAccount(10L);
        Student student = student(account);
        given(accountRepository.findByIdForUpdate(10L)).willReturn(Optional.of(account));
        given(studentRepository.findByIdForUpdate(10L)).willReturn(Optional.of(student));

        // when & then
        assertThatThrownBy(() -> adminStudentService.resendCredentialMail(ADMIN_ID, 10L))
                .isInstanceOf(StudentException.class)
                .extracting("errorCode").isEqualTo(StudentErrorType.CREDENTIAL_MAIL_PENDING);
        verify(passwordEncoder, never()).encode(anyString());
        verify(mailOutboxService, never()).enqueue(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void deactivate_진행중신청과활성지원을취소하고계정을비활성화한다() {
        // given
        givenFullAdmin();
        Account account = studentAccount(10L);
        Student student = student(account);
        Helper helper = helper(20L);
        HelpRequest recruiting = helpRequest(student, 101L, NOW.plusDays(1));
        HelpRequest matched = helpRequest(student, 102L, NOW.plusDays(2));
        matched.match(NOW);
        Application matchedApplication = new Application(matched, helper, NOW);
        matchedApplication.match(NOW);
        Application waitingApplication = new Application(matched, helper, NOW.plusMinutes(1));

        given(accountRepository.findByIdForUpdate(10L)).willReturn(Optional.of(account));
        given(studentRepository.findByIdForUpdate(10L)).willReturn(Optional.of(student));
        given(helpRequestRepository.findActiveIdsByStudentAccountId(10L)).willReturn(List.of(101L, 102L));
        given(helpRequestRepository.findByIdForUpdate(101L)).willReturn(Optional.of(recruiting));
        given(helpRequestRepository.findByIdForUpdate(102L)).willReturn(Optional.of(matched));
        given(applicationRepository.findActiveByHelpRequestIdForUpdate(101L)).willReturn(List.of());
        given(applicationRepository.findActiveByHelpRequestIdForUpdate(102L))
                .willReturn(List.of(matchedApplication, waitingApplication));

        // when
        AdminStudentAccountStatusResponseDto result = adminStudentService.deactivate(ADMIN_ID, 10L);

        // then
        assertThat(account.getStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(account.getDeactivatedAt()).isEqualTo(NOW);
        assertThat(recruiting.getStatus()).isEqualTo(HelpRequestStatus.CANCELED);
        assertThat(matched.getStatus()).isEqualTo(HelpRequestStatus.CANCELED);
        assertThat(matchedApplication.getStatus()).isEqualTo(ApplicationStatus.STUDENT_CANCELED);
        assertThat(waitingApplication.getStatus()).isEqualTo(ApplicationStatus.STUDENT_CANCELED);
        assertThat(result.status()).isEqualTo(AccountStatus.INACTIVE);
        verify(passwordResetTokenRepository).invalidateAllByAccountId(10L, NOW);

        InOrder lockOrder = org.mockito.Mockito.inOrder(
                accountRepository, studentRepository, helpRequestRepository, applicationRepository);
        lockOrder.verify(accountRepository).findByIdForUpdate(10L);
        lockOrder.verify(studentRepository).findByIdForUpdate(10L);
        lockOrder.verify(helpRequestRepository).findActiveIdsByStudentAccountId(10L);
        lockOrder.verify(helpRequestRepository).findByIdForUpdate(101L);
        lockOrder.verify(applicationRepository).findActiveByHelpRequestIdForUpdate(101L);
        lockOrder.verify(helpRequestRepository).findByIdForUpdate(102L);
        lockOrder.verify(applicationRepository).findActiveByHelpRequestIdForUpdate(102L);
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
