package com.hankkiatti.domain.student.service;

import com.hankkiatti.domain.mail.entity.MailType;
import com.hankkiatti.domain.mail.event.MailFailedEvent;
import com.hankkiatti.domain.mail.event.MailSentEvent;
import com.hankkiatti.domain.student.entity.Student;
import com.hankkiatti.domain.student.repository.StudentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StudentCredentialMailListener {

    private final StudentRepository studentRepository;
    private final Clock clock;

    @EventListener
    @Transactional
    public void onSent(MailSentEvent event) {
        if (event.mailType() != MailType.STUDENT_CREDENTIAL || event.referenceId() == null) {
            return;
        }
        studentRepository.findById(event.referenceId())
                .ifPresent(student -> student.markCredentialMailSent(LocalDateTime.now(clock)));
    }

    @EventListener
    @Transactional
    public void onFailed(MailFailedEvent event) {
        if (event.mailType() != MailType.STUDENT_CREDENTIAL || event.referenceId() == null) {
            return;
        }
        studentRepository.findById(event.referenceId())
                .ifPresent(Student::markCredentialMailFailed);
    }
}
