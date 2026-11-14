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
import java.util.Locale;
import java.util.Map;

/** Renders templates/mail/email-verification.html in the request's locale (Accept-Language) and sends it over SMTP. */
@Component
@RequiredArgsConstructor
@Slf4j
class VerificationMailer {
    private final JavaMailSender mail;
    private final ITemplateEngine templates;
    private final MessageSource messages;
    private final AuthProperties props;

    /** Never throws: the account already exists, so a mail failure is logged instead of failing signup. */
    // shortcut: sent synchronously in the signup request; move to RabbitMQ/@Async if SMTP latency hurts signup
    void sendVerification(User user, String token) {
        Locale locale = LocaleContextHolder.getLocale();
        Context ctx = new Context(locale, Map.of(
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "verifyUrl", props.appUrl() + "/verify-email?token=" + token,
                "expiryHours", props.emailVerificationTtl().toHours(),
                "logoUrl", props.appUrl() + "/thoth-logo-mark.png",
                "year", Year.now().getValue()));
        try {
            MimeMessage msg = mail.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, "UTF-8");
            h.setFrom(props.mailFrom());
            h.setTo(user.getEmail());
            h.setSubject(messages.getMessage("email.verify.subject", null, locale));
            h.setText(templates.process("mail/email-verification", ctx), true);
            mail.send(msg);
        } catch (MessagingException | MailException e) {
            log.warn("Verification email to user {} failed", user.getId(), e);
        }
    }
}
