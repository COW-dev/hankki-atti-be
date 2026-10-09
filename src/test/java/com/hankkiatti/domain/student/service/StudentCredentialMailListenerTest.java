package com.hankkiatti.domain.student.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hankkiatti.domain.account.entity.Account;
import com.hankkiatti.domain.account.entity.AccountRole;
import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.event.MailFailedEvent;
import com.hankkiatti.domain.mail.event.MailSentEvent;
import com.hankkiatti.domain.student.entity.CredentialMailStatus;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import com.hankkiatti.support.TestProfiles;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StudentCredentialMailListenerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 9, 15, 0);

    @Mock
    private StudentRepository studentRepository;

    private StudentCredentialMailListener listener;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        listener = new StudentCredentialMailListener(studentRepository, clock);
    }

    private Student student() {
        Account account = new Account("60261234", "hash", AccountRole.STUDENT, true, true);
        ReflectionTestUtils.setField(account, "id", 10L);
        Student student = TestProfiles.student(account);
        ReflectionTestUtils.setField(student, "accountId", 10L);
        return student;
    }

    @Test
    void onSent_계정정보메일_발송완료로변경한다() {
        // given
        Student student = student();
        given(studentRepository.findById(10L)).willReturn(Optional.of(student));

        // when
        listener.onSent(new MailSentEvent(1L, MailType.STUDENT_CREDENTIAL, 10L));

        // then
        assertThat(student.getCredentialMailStatus()).isEqualTo(CredentialMailStatus.SENT);
        assertThat(student.getCredentialMailSentAt()).isEqualTo(NOW);
    }

    @Test
    void onFailed_계정정보메일_발송실패로변경한다() {
        // given
        Student student = student();
        given(studentRepository.findById(10L)).willReturn(Optional.of(student));

        // when
        listener.onFailed(new MailFailedEvent(1L, MailType.STUDENT_CREDENTIAL, 10L));

        // then
        assertThat(student.getCredentialMailStatus()).isEqualTo(CredentialMailStatus.FAILED);
    }
}
