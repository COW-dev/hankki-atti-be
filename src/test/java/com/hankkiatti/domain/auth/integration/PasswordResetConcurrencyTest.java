package com.hankkiatti.domain.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.account.repository.AccountRepository;
import com.hankkiatti.domain.auth.dto.request.PasswordResetConfirmRequestDto;
import com.hankkiatti.domain.auth.dto.request.PasswordResetRequestDto;
import com.hankkiatti.domain.auth.exception.AuthErrorType;
import com.hankkiatti.domain.auth.exception.AuthException;
import com.hankkiatti.domain.auth.repository.PasswordResetTokenRepository;
import com.hankkiatti.domain.auth.service.PasswordResetService;
import com.hankkiatti.domain.mail.entity.MailOutbox;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.repository.MailOutboxRepository;
import com.hankkiatti.domain.student.entity.DisabilityType;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 같은 아이디·같은 링크로 동시에 들어온 요청을 실제 트랜잭션·락으로 확인한다. 테스트마다 커밋하므로 끝나면 지운다.
 */
@SpringBootTest
class PasswordResetConcurrencyTest {

    private static final String STUDENT_NO = "60239999";
    private static final String TOKEN_MARKER = "/password-reset#token=";
    private static final int THREADS = 2;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private MailOutboxRepository mailOutboxRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long accountId;

    @BeforeEach
    void setUp() {
        Account account = accountRepository.save(new Account(STUDENT_NO, passwordEncoder.encode("hankki!2026"),
                AccountRole.STUDENT, false, false));
        studentRepository.save(new Student(account, "김학생", STUDENT_NO, "010-0000-0000", "kakao",
                STUDENT_NO + "@mju.ac.kr", DisabilityType.PHYSICAL, null));
        accountId = account.getId();
    }

    // 같은 컨텍스트의 다른 테스트 데이터를 건드리지 않게 이 테스트가 만든 계정만 지운다.
    // 재설정 토큰·아웃박스는 커밋하는 다른 테스트가 없어 전부 지운다 (MailOutboxIntegrationTest와 같음)
    @AfterEach
    void tearDown() {
        passwordResetTokenRepository.deleteAll();
        mailOutboxRepository.deleteAll();
        studentRepository.deleteById(accountId);
        accountRepository.deleteById(accountId);
    }

    private List<MailOutbox> resetMails() {
        return mailOutboxRepository.findAll().stream()
                .filter(mail -> mail.getMailType() == MailType.PASSWORD_RESET)
                .toList();
    }

    /**
     * 같은 작업을 여러 스레드에서 한꺼번에 시작하고, 스레드별로 성공했으면 null, 실패했으면 예외를 돌려준다.
     */
    private List<Throwable> runConcurrently(Callable<Void> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        task.call();
                        return null;
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            ready.await();
            start.countDown();

            List<Throwable> results = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void request_같은아이디동시요청_메일은한통() throws Exception {
        // when
        List<Throwable> results = runConcurrently(() -> {
            passwordResetService.request(new PasswordResetRequestDto(STUDENT_NO));
            return null;
        });

        // then
        assertThat(results).containsOnlyNulls();
        assertThat(resetMails()).hasSize(1);
        assertThat(passwordResetTokenRepository.count()).isEqualTo(1);
    }

    @Test
    void confirm_같은링크동시완료_한건만성공() throws Exception {
        // given
        passwordResetService.request(new PasswordResetRequestDto(STUDENT_NO));
        String body = resetMails().get(0).getBody();
        int start = body.indexOf(TOKEN_MARKER) + TOKEN_MARKER.length();
        String token = body.substring(start, body.indexOf('\n', start));

        // when
        List<Throwable> results = runConcurrently(() -> {
            passwordResetService.confirm(new PasswordResetConfirmRequestDto(token, "newPass!2026"));
            return null;
        });

        // then
        assertThat(results).filteredOn(result -> result == null).hasSize(1);
        assertThat(results).filteredOn(result -> result != null).singleElement()
                .isInstanceOf(AuthException.class)
                .extracting("errorCode").isEqualTo(AuthErrorType.PASSWORD_RESET_LINK_INVALID);
        assertThat(accountRepository.findByLoginId(STUDENT_NO).orElseThrow().getTokenVersion()).isEqualTo(1);
    }
}
