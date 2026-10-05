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
import java.util.Set;
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
}
