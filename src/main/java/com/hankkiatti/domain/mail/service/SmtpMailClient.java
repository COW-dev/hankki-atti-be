package com.hankkiatti.domain.mail.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP로 텍스트 메일 한 통을 보낸다.
 * spring.mail.host가 없으면 JavaMailSender 빈이 없다 → 보내지 않고 메일을 대기 상태로 둔다 (설정 전 메일이 실패로 버려지지 않게).
 */
@Component
@RequiredArgsConstructor
public class SmtpMailClient {

    private final ObjectProvider<JavaMailSender> javaMailSender;
    private final MailProperties mailProperties;

    public boolean isConfigured() {
        return javaMailSender.getIfAvailable() != null;
    }

    public void send(String recipient, String subject, String body) {
        JavaMailSender sender = javaMailSender.getObject();
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.fromAddress(), mailProperties.fromName());
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body, false);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailPreparationException("메일 작성 실패", e);
        }
        sender.send(message);
    }
}
