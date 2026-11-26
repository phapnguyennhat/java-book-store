package com.example.book_store.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Year;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Renders templates/mail/*.html in the request's locale (Accept-Language) and sends them over SMTP. */
@Component
@RequiredArgsConstructor
@Slf4j
class AuthMailer {
    private final JavaMailSender mail;
    private final ITemplateEngine templates;
    private final MessageSource messages;
    private final AuthProperties props;

    void sendVerification(User user, String token) {
        send(user, "mail/email-verification", "email.verify.subject", Map.of(
                "verifyUrl", props.appUrl() + "/verify-email?token=" + token,
                "expiryHours", props.emailVerificationTtl().toHours()));
    }

    void sendPasswordReset(User user, String token) {
        send(user, "mail/password-reset", "email.reset.subject", Map.of(
                "resetUrl", props.appUrl() + "/reset-password?token=" + token,
                "expiryMinutes", props.passwordResetTtl().toMinutes()));
    }

    /** Never throws: callers answer the same whether or not mail went out, so a failure is only logged. */
    // shortcut: sent synchronously in the request; move to RabbitMQ/@Async if SMTP latency hurts
    private void send(User user, String template, String subjectKey, Map<String, Object> vars) {
        Locale locale = LocaleContextHolder.getLocale();
        Map<String, Object> model = new HashMap<>(vars);
        model.put("fullName", user.getFullName());
        model.put("email", user.getEmail());
        model.put("logoUrl", props.appUrl() + "/thoth-logo-mark.png");
        model.put("year", Year.now().getValue());
        try {
            MimeMessage msg = mail.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, "UTF-8");
            h.setFrom(props.mailFrom());
            h.setTo(user.getEmail());
            h.setSubject(messages.getMessage(subjectKey, null, locale));
            h.setText(templates.process(template, new Context(locale, model)), true);
            mail.send(msg);
        } catch (MessagingException | MailException e) {
            log.warn("Email {} to user {} failed", template, user.getId(), e);
        }
    }
}
