package com.hankkiatti.domain.helprequest.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.application.entity.Application;
import com.hankkiatti.domain.application.entity.ApplicationStatus;
import com.hankkiatti.domain.application.repository.ApplicationRepository;
import com.hankkiatti.domain.helper.entity.Helper;
import com.hankkiatti.domain.helper.repository.HelperRepository;
import com.hankkiatti.domain.helprequest.entity.HelpRequest;
import com.hankkiatti.domain.helprequest.entity.HelpRequestStatus;
import com.hankkiatti.domain.helprequest.event.HelpRequestFailedEvent;
import com.hankkiatti.domain.helprequest.repository.HelpRequestRepository;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestHelpRequests;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여러 상태의 신청을 실제 DB(H2)에 넣고 한 번 돌린 결과를 확인한다.
 */
@SpringBootTest
@Transactional
@RecordApplicationEvents
class MealTimeIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 12, 0);

    @Autowired
    private MealTimeJob mealTimeJob;

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

    @Autowired
    private ApplicationEvents applicationEvents;

    private Student saveStudent(String studentNo) {
        Student student = TestHelpRequests.student(studentNo);
        accountRepository.save(student.getAccount());
        return studentRepository.save(student);
    }

    private Helper saveHelper(String studentNo) {
        Helper helper = TestHelpRequests.helper(studentNo);
        accountRepository.save(helper.getAccount());
        return helperRepository.save(helper);
    }

    private HelpRequest saveRequest(Student student, LocalDateTime startAt) {
        return helpRequestRepository.save(TestHelpRequests.request(student, startAt));
    }

    private Application saveApplication(HelpRequest request, Helper helper, boolean matched) {
        Application application = new Application(request, helper, request.getStartAt().minusHours(5));
        if (matched) {
            application.match(request.getStartAt().minusHours(5));
        }
        return applicationRepository.save(application);
    }

    @Test
    void processDue_식사시작과종료가지난신청을처리하고_다시돌려도그대로() {
        // given
        Student student = saveStudent("60231234");
        Helper helperA = saveHelper("60230001");
        Helper helperB = saveHelper("60230002");

        // 지원자 없이 시작 시각이 지남 → 매칭 실패
        HelpRequest noApplicant = saveRequest(student, NOW.minusMinutes(30));
        // 식사 중 (11:30~12:30) → 예비만 종료, 매칭은 유지
        HelpRequest inMeal = saveRequest(student, NOW.minusMinutes(30));
        inMeal.match(NOW.minusHours(5));
        Application inMealMatched = saveApplication(inMeal, helperA, true);
        Application inMealWaiting = saveApplication(inMeal, helperB, false);
        // 식사 끝남 (10:30~11:30) → 이용 완료
        HelpRequest finished = saveRequest(student, NOW.minusMinutes(90));
        finished.match(NOW.minusHours(5));
        Application finishedMatched = saveApplication(finished, helperB, true);
        // 아직 시작 전 → 그대로
        HelpRequest upcoming = saveRequest(student, NOW.plusMinutes(30));
        entityManager.flush();
        entityManager.clear();

        // when
        mealTimeJob.processDue(NOW);
        mealTimeJob.processDue(NOW);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(helpRequestRepository.findById(noApplicant.getId()).orElseThrow().getStatus())
                .isEqualTo(HelpRequestStatus.FAILED);
        assertThat(helpRequestRepository.findById(inMeal.getId()).orElseThrow().getStatus())
                .isEqualTo(HelpRequestStatus.MATCHED);
        assertThat(applicationRepository.findById(inMealMatched.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.MATCHED);
        assertThat(applicationRepository.findById(inMealWaiting.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.EXPIRED);

        HelpRequest completed = helpRequestRepository.findById(finished.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(HelpRequestStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isEqualTo(NOW);
        Application volunteered = applicationRepository.findById(finishedMatched.getId()).orElseThrow();
        assertThat(volunteered.getStatus()).isEqualTo(ApplicationStatus.COMPLETED);
        assertThat(volunteered.getVolunteerHours()).isEqualByComparingTo(new BigDecimal("1.0"));

        assertThat(helpRequestRepository.findById(upcoming.getId()).orElseThrow().getStatus())
                .isEqualTo(HelpRequestStatus.RECRUITING);

        // 두 번 돌렸어도 매칭 실패 알림 이벤트는 한 번만
        assertThat(applicationEvents.stream(HelpRequestFailedEvent.class))
                .containsExactly(new HelpRequestFailedEvent(noApplicant.getId(), student.getAccountId(), NOW.minusMinutes(30)));
    }

    @Test
    void findIdsToStart와Complete_처리할것이남은신청만() {
        // given
        Student student = saveStudent("60231234");
        Helper helper = saveHelper("60230001");
        HelpRequest recruiting = saveRequest(student, NOW.minusMinutes(30));
        HelpRequest matchedNoWaiting = saveRequest(student, NOW.minusMinutes(30));
        matchedNoWaiting.match(NOW.minusHours(5));
        saveApplication(matchedNoWaiting, helper, true);
        HelpRequest ended = saveRequest(student, NOW.minusMinutes(60));
        ended.match(NOW.minusHours(5));
        HelpRequest canceled = saveRequest(student, NOW.minusMinutes(30));
        canceled.withdraw(NOW.minusHours(1));
        entityManager.flush();

        // when & then — 예비가 없는 매칭 완료 신청은 시작 처리할 게 없다
        assertThat(helpRequestRepository.findIdsToStart(NOW, 200)).containsExactly(recruiting.getId());
        assertThat(helpRequestRepository.findIdsToComplete(NOW, 200)).containsExactly(ended.getId());
    }
}
