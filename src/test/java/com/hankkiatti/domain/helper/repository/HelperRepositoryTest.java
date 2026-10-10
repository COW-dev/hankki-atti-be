package com.hankkiatti.domain.helper.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.entity.AccountStatus;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.application.repository.HelperApplicationCount;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpType;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.global.config.JpaConfig;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
// 테스트 설정의 H2(MySQL 모드)를 그대로 쓴다 (DomainRepositoryTest 주석 참고)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class HelperRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 10, 0);
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20);

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private HelperRepository helperRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private HelpRequestRepository helpRequestRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private EntityManager entityManager;

    private Helper hanjiwoo;
    private Helper yuntaeho;
    private Helper kangminho;

    @BeforeEach
    void setUp() {
        hanjiwoo = saveHelper("hanjw@mju.ac.kr", "한지우", "20221111", true);
        yuntaeho = saveHelper("yunth@mju.ac.kr", "윤태호", "20213333", false);
        kangminho = saveHelper("kangmh@mju.ac.kr", "강민호", "20205555", true);
        kangminho.getAccount().deactivate(NOW);
        entityManager.flush();
        entityManager.clear();
    }

    private Helper saveHelper(String email, String name, String studentNo, boolean attiMember) {
        Account account = accountRepository.save(new Account(email, "hash", AccountRole.HELPER, false, false));
        return helperRepository.save(new Helper(account, name, studentNo, email, "010-1111-1111", "kakao", attiMember,
                NOW));
    }

    private List<Long> ids(Page<Helper> page) {
        return page.map(Helper::getAccountId).toList();
    }

    @Test
    void searchForAdmin_조건없음_가입최근순전부() {
        // when
        Page<Helper> page = helperRepository.searchForAdmin(null, null, null, FIRST_PAGE);

        // then
        assertThat(ids(page)).containsExactly(
                kangminho.getAccountId(), yuntaeho.getAccountId(), hanjiwoo.getAccountId());
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void searchForAdmin_이름또는학번부분일치() {
        // when
        Page<Helper> byName = helperRepository.searchForAdmin("%태호%", null, null, FIRST_PAGE);
        Page<Helper> byStudentNo = helperRepository.searchForAdmin("%1111%", null, null, FIRST_PAGE);

        // then
        assertThat(ids(byName)).containsExactly(yuntaeho.getAccountId());
        assertThat(ids(byStudentNo)).containsExactly(hanjiwoo.getAccountId());
    }

    @Test
    void searchForAdmin_상태와아띠소속필터조합() {
        // when
        Page<Helper> activeAtti = helperRepository.searchForAdmin(null, AccountStatus.ACTIVE, true, FIRST_PAGE);
        Page<Helper> inactive = helperRepository.searchForAdmin(null, AccountStatus.INACTIVE, null, FIRST_PAGE);

        // then
        assertThat(ids(activeAtti)).containsExactly(hanjiwoo.getAccountId());
        assertThat(ids(inactive)).containsExactly(kangminho.getAccountId());
    }

    @Test
    void searchForAdmin_페이지_나눠서주고전체인원은그대로() {
        // when
        Page<Helper> second = helperRepository.searchForAdmin(null, null, null, PageRequest.of(1, 2));

        // then
        assertThat(ids(second)).containsExactly(hanjiwoo.getAccountId());
        assertThat(second.getTotalElements()).isEqualTo(3);
        assertThat(second.getTotalPages()).isEqualTo(2);
    }

    @Test
    void countCompletedByHelper_이용완료만도우미별로센다() {
        // given
        Account studentAccount = accountRepository.save(
                new Account("60201234", "hash", AccountRole.STUDENT, false, false));
        Student student = studentRepository.save(new Student(studentAccount, "김학생", "60201234", "010-0000-0000",
                "kakao", "student@mju.ac.kr", DisabilityType.VISUAL, null));
        Helper helper = helperRepository.findById(hanjiwoo.getAccountId()).orElseThrow();
        for (int day = 1; day <= 3; day++) {
            HelpRequest request = helpRequestRepository.save(new HelpRequest(student, NOW.plusDays(day),
                    Set.of(HelpType.SERVING), null, null));
            Application application = new Application(request, helper, NOW);
            application.match(NOW);
            if (day < 3) {
                application.complete();
            }
            applicationRepository.save(application);
        }
        entityManager.flush();

        // when
        List<HelperApplicationCount> counts = applicationRepository.countCompletedByHelper(
                List.of(hanjiwoo.getAccountId(), yuntaeho.getAccountId()));

        // then
        assertThat(counts).containsExactly(new HelperApplicationCount(hanjiwoo.getAccountId(), 2));
    }
}
