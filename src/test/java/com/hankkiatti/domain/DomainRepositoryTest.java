package com.hankkiatti.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.CredentialMailStatus;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.global.config.JpaConfig;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
// 기본 내장 H2로 바꾸면 enum CHECK 제약 평가 중 "database has been closed"로 INSERT가 실패한다. 테스트 설정의 H2(MySQL 모드)를 그대로 쓴다
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class DomainRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 10, 0);

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private HelperRepository helperRepository;

    @Autowired
    private HelpRequestRepository helpRequestRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private EntityManager entityManager;

    private Account saveAccount(String loginId, AccountRole role) {
        return accountRepository.save(new Account(loginId, "hash", role, false, false));
    }

    private Student saveStudent() {
        Account account = saveAccount("60201234", AccountRole.STUDENT);
        return studentRepository.save(new Student(account, "김학생", "60201234", "010-0000-0000",
                "kakao", "student@mju.ac.kr", DisabilityType.VISUAL, null));
    }

    private Helper saveHelper() {
        Account account = saveAccount("helper@mju.ac.kr", AccountRole.HELPER);
        return helperRepository.save(new Helper(account, "이도우미", "60205678", "helper@mju.ac.kr",
                "010-1111-1111", "kakao2", true, NOW));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void findByLoginId_저장된계정_조회() {
        // given
        Account saved = saveAccount("student01", AccountRole.STUDENT);
        flushAndClear();

        // when
        Account found = accountRepository.findByLoginId("student01").orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(accountRepository.existsByLoginId("student01")).isTrue();
        assertThat(accountRepository.existsByLoginId("unknown")).isFalse();
    }

    @Test
    void save_로그인아이디중복_무결성예외() {
        // given
        accountRepository.saveAndFlush(new Account("dup", "hash", AccountRole.HELPER, false, false));

        // when & then
        assertThatThrownBy(() -> accountRepository.saveAndFlush(
                new Account("dup", "hash", AccountRole.HELPER, false, false)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_학생_계정과PK공유() {
        // given
        Student saved = saveStudent();
        flushAndClear();

        // when
        Student found = studentRepository.findById(saved.getAccountId()).orElseThrow();

        // then
        assertThat(found.getAccountId()).isEqualTo(found.getAccount().getId());
        assertThat(found.getCredentialMailStatus()).isEqualTo(CredentialMailStatus.PENDING);
        assertThat(studentRepository.existsByStudentNo("60201234")).isTrue();
        assertThat(studentRepository.existsByStudentNo("60209999")).isFalse();
    }

    @Test
    void save_도우미_계정과PK공유() {
        // given
        Helper saved = saveHelper();
        flushAndClear();

        // when
        Helper found = helperRepository.findById(saved.getAccountId()).orElseThrow();

        // then
        assertThat(found.getAccountId()).isEqualTo(found.getAccount().getId());
        assertThat(helperRepository.existsByEmail("helper@mju.ac.kr")).isTrue();
        assertThat(helperRepository.existsByStudentNo("60205678")).isTrue();
        assertThat(helperRepository.existsByEmail("other@mju.ac.kr")).isFalse();
    }

    @Test
    void save_도움신청_도움유형컬렉션왕복() {
        // given
        Student student = saveStudent();
        HelpRequest saved = helpRequestRepository.save(new HelpRequest(student, NOW.plusHours(2),
                Set.of(HelpType.SERVING, HelpType.OTHER), "식판 정리", null));
        flushAndClear();

        // when
        HelpRequest found = helpRequestRepository.findById(saved.getId()).orElseThrow();

        // then
        assertThat(found.getHelpTypes()).containsExactlyInAnyOrder(HelpType.SERVING, HelpType.OTHER);
        assertThat(found.getEndAt()).isEqualTo(NOW.plusHours(3));
    }

    @Test
    void save_같은신청같은도우미재지원_두건저장() {
        // given
        Student student = saveStudent();
        Helper helper = saveHelper();
        HelpRequest request = helpRequestRepository.save(
                new HelpRequest(student, NOW.plusHours(2), Set.of(HelpType.SEATING), null, null));
        Application first = new Application(request, helper, NOW);
        first.withdraw(NOW.plusMinutes(1));
        applicationRepository.save(first);

        // when
        applicationRepository.save(new Application(request, helper, NOW.plusMinutes(2)));
        flushAndClear();

        // then
        assertThat(applicationRepository.count()).isEqualTo(2);
    }

    @Test
    void existsOverlapping_30분겹치면있음_끝과시작이같으면없음() {
        // given — 12:00~13:00 모집 중
        Student student = saveStudent();
        LocalDateTime noon = LocalDateTime.of(2026, 10, 12, 12, 0);
        helpRequestRepository.save(new HelpRequest(student, noon, Set.of(HelpType.SERVING), null, null));
        flushAndClear();
        Long studentId = student.getAccountId();

        // when & then
        assertThat(helpRequestRepository.existsOverlapping(studentId, noon.plusMinutes(30), noon.plusMinutes(90)))
                .isTrue();
        assertThat(helpRequestRepository.existsOverlapping(studentId, noon.minusMinutes(30), noon.plusMinutes(30)))
                .isTrue();
        assertThat(helpRequestRepository.existsOverlapping(studentId, noon.plusHours(1), noon.plusHours(2))).isFalse();
        assertThat(helpRequestRepository.existsOverlapping(studentId, noon.minusHours(1), noon)).isFalse();
    }

    @Test
    void existsOverlapping_매칭완료는세고_철회와매칭실패와다른장애학생건은세지않음() {
        // given
        Student student = saveStudent();
        Account otherAccount = saveAccount("60209999", AccountRole.STUDENT);
        Student other = studentRepository.save(new Student(otherAccount, "박학생", "60209999", "010-0000-0000",
                "kakao3", "other@mju.ac.kr", DisabilityType.VISUAL, null));
        LocalDateTime noon = LocalDateTime.of(2026, 10, 12, 12, 0);
        LocalDateTime evening = LocalDateTime.of(2026, 10, 12, 17, 0);

        HelpRequest withdrawn = new HelpRequest(student, noon, Set.of(HelpType.SERVING), null, null);
        withdrawn.withdraw(NOW);
        HelpRequest failed = new HelpRequest(student, noon, Set.of(HelpType.SERVING), null, null);
        failed.fail();
        HelpRequest matched = new HelpRequest(student, evening, Set.of(HelpType.SERVING), null, null);
        matched.match(NOW);
        helpRequestRepository.save(withdrawn);
        helpRequestRepository.save(failed);
        helpRequestRepository.save(matched);
        helpRequestRepository.save(new HelpRequest(other, noon, Set.of(HelpType.SERVING), null, null));
        flushAndClear();

        // when & then
        assertThat(helpRequestRepository.existsOverlapping(student.getAccountId(), noon, noon.plusHours(1)))
                .isFalse();
        assertThat(helpRequestRepository.existsOverlapping(student.getAccountId(), evening, evening.plusHours(1)))
                .isTrue();
    }

    @Test
    void findMatchedWithHelper_매칭완료와이용완료만_도우미까지한번에() {
        // given
        Student student = saveStudent();
        Helper helper = saveHelper();
        LocalDateTime noon = LocalDateTime.of(2026, 10, 12, 12, 0);
        HelpRequest matchedRequest = helpRequestRepository.save(
                new HelpRequest(student, noon, Set.of(HelpType.SERVING), null, null));
        HelpRequest completedRequest = helpRequestRepository.save(
                new HelpRequest(student, noon.plusDays(1), Set.of(HelpType.SERVING), null, null));
        Application matched = new Application(matchedRequest, helper, NOW);
        matched.match(NOW);
        Application completed = new Application(completedRequest, helper, NOW);
        completed.match(NOW);
        completed.complete();
        Application waiting = new Application(matchedRequest, helper, NOW.plusMinutes(1));
        applicationRepository.save(matched);
        applicationRepository.save(completed);
        applicationRepository.save(waiting);
        flushAndClear();

        // when
        List<Application> result = applicationRepository.findMatchedWithHelper(
                List.of(matchedRequest.getId(), completedRequest.getId()));

        // then
        assertThat(result).extracting(Application::getId).containsExactlyInAnyOrder(matched.getId(), completed.getId());
        assertThat(result).allSatisfy(application -> assertThat(Hibernate.isInitialized(application.getHelper())).isTrue());
    }

    @Test
    void findOpen_모집중과매칭완료만_시작지난건과기간밖은빼고시작시각순() {
        // given
        Student student = saveStudent();
        LocalDateTime now = LocalDateTime.of(2026, 10, 12, 12, 0);
        LocalDateTime from = LocalDateTime.of(2026, 10, 12, 0, 0);
        LocalDateTime toExclusive = LocalDateTime.of(2026, 10, 14, 0, 0);

        HelpRequest later = helpRequestRepository.save(
                new HelpRequest(student, now.plusDays(1).plusHours(5), Set.of(HelpType.SERVING), null, null));
        HelpRequest matched = new HelpRequest(student, now.plusDays(1), Set.of(HelpType.SERVING), null, null);
        matched.match(now);
        helpRequestRepository.save(matched);
        HelpRequest soon = helpRequestRepository.save(
                new HelpRequest(student, now.plusMinutes(30), Set.of(HelpType.SERVING), null, null));
        // 시작 시각 = 지금은 "시작 지난 건"이다
        helpRequestRepository.save(new HelpRequest(student, now, Set.of(HelpType.SERVING), null, null));
        HelpRequest canceled = new HelpRequest(student, now.plusHours(5), Set.of(HelpType.SERVING), null, null);
        canceled.withdraw(now);
        helpRequestRepository.save(canceled);
        helpRequestRepository.save(new HelpRequest(student, toExclusive, Set.of(HelpType.SERVING), null, null));
        flushAndClear();

        // when
        List<HelpRequest> result = helpRequestRepository.findOpen(from, toExclusive, now);

        // then
        assertThat(result).extracting(HelpRequest::getId)
                .containsExactly(soon.getId(), matched.getId(), later.getId());
    }

    @Test
    void findActiveWithHelpRequestByHelperId_진행중지원만_신청까지한번에() {
        // given
        Student student = saveStudent();
        Helper helper = saveHelper();
        LocalDateTime noon = LocalDateTime.of(2026, 10, 12, 12, 0);
        HelpRequest request = helpRequestRepository.save(
                new HelpRequest(student, noon, Set.of(HelpType.SERVING), null, null));
        Application matched = new Application(request, helper, NOW);
        matched.match(NOW);
        Application pending = new Application(request, helper, NOW);
        pending.promote(NOW, NOW.plusMinutes(30), null);
        Application waiting = new Application(request, helper, NOW);
        Application withdrawn = new Application(request, helper, NOW);
        withdrawn.withdraw(NOW);
        Application completed = new Application(request, helper, NOW);
        completed.match(NOW);
        completed.complete();
        applicationRepository.save(matched);
        applicationRepository.save(pending);
        applicationRepository.save(waiting);
        applicationRepository.save(withdrawn);
        applicationRepository.save(completed);
        flushAndClear();

        // when
        List<Application> result = applicationRepository.findActiveWithHelpRequestByHelperId(helper.getAccountId());

        // then
        assertThat(result).extracting(Application::getId)
                .containsExactlyInAnyOrder(matched.getId(), pending.getId(), waiting.getId());
        assertThat(result).allSatisfy(
                application -> assertThat(Hibernate.isInitialized(application.getHelpRequest())).isTrue());
    }
}
