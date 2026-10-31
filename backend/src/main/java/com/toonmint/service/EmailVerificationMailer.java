package com.toonmint.service;

import com.toonmint.config.AppProperties;
import com.toonmint.domain.VerificationPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;

/**
 * Trimite codurile OTP prin SMTP. Când `app.mail.enabled=false` (dezvoltare),
 * codul este doar scris în log, ca fluxul să poată fi testat local.
 * Emailurile cu coduri nu sunt salvate în `email_messages`, ca să nu persistăm
 * coduri în clar.
 */
@Service
public class EmailVerificationMailer implements VerificationMailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationMailer.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppProperties properties;

    /**
     * Injectează senderul SMTP (opțional) și configurația de email.
     */
    public EmailVerificationMailer(ObjectProvider<JavaMailSender> mailSenderProvider, AppProperties properties) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
    }

    /**
     * Trimite codul către utilizator; erorile SMTP sunt doar logate, ca înregistrarea
     * să nu eșueze din cauza unui provider indisponibil (codul poate fi retrimis).
     */
    @Override
    public void send(String email, String code, VerificationPurpose purpose, Instant expiresAt) {
        String subject = subjectFor(purpose);
        long minutes = Math.max(1, Duration.between(Instant.now(), expiresAt).toMinutes());
        String text = textBodyFor(purpose, code, minutes);
        String html = htmlBodyFor(purpose, code, minutes);
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!properties.mail().enabled() || mailSender == null) {
            log.warn("Mail disabled (app.mail.enabled=false). {} code for {} is {} (valid until {})",
                    purpose, email, code, expiresAt);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            String fromName = properties.mail().fromName();
            helper.setFrom(fromName == null || fromName.isBlank()
                    ? properties.mail().from()
                    : fromName + " <" + properties.mail().from() + ">");
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
            log.info("Sent {} code to {}", purpose, email);
        } catch (RuntimeException | MessagingException ex) {
            log.warn("Could not send {} email to {}: {}", purpose, email, ex.getMessage());
        }
    }

    /**
     * Subiectul emailului, în funcție de scopul codului.
     */
    private static String subjectFor(VerificationPurpose purpose) {
        return purpose == VerificationPurpose.PASSWORD_RESET
                ? "Reset your ToonMint password"
                : "Verify your ToonMint email address";
    }

    /**
     * Corpul emailului, cu codul și durata de valabilitate.
     */
    private static String textBodyFor(VerificationPurpose purpose, String code, long minutes) {
        String intro = purpose == VerificationPurpose.PASSWORD_RESET
                ? "We received a request to reset your ToonMint password."
                : "Welcome to ToonMint! Confirm your email address to activate your account.";
        String action = purpose == VerificationPurpose.PASSWORD_RESET
                ? "Enter this code on the password reset page to choose a new password."
                : "Enter this code on the verification page to activate your account.";
        return intro + "\n\n"
                + code + " is your one-time verification code for your ToonMint account.\n\n"
                + action + "\n"
                + "This code expires in " + minutes + " minutes and can only be used once.\n\n"
                + "If you didn't request this code, you can safely ignore this email.";
    }

    private static String htmlBodyFor(VerificationPurpose purpose, String code, long minutes) {
        String title = purpose == VerificationPurpose.PASSWORD_RESET ? "Reset your password" : "Verify your email";
        String intro = purpose == VerificationPurpose.PASSWORD_RESET
                ? "We received a request to reset your ToonMint password. Use the code below to continue."
                : "Welcome to ToonMint! Confirm your email address to activate your account.";
        String action = purpose == VerificationPurpose.PASSWORD_RESET
                ? "Enter this code on the password reset page to choose a new password."
                : "Enter this code on the verification page to activate your account.";
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:32px 12px;background:#f5f3f8;font-family:Arial,Helvetica,sans-serif;color:#242136">
                  <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="max-width:560px;margin:0 auto;background:#fff;border-radius:18px;overflow:hidden">
                    <tr><td style="padding:28px 32px 10px">
                      <div style="font-size:18px;font-weight:700;color:#302653"><span style="display:inline-block;background:#ff9b50;color:#fff;border-radius:9px;padding:7px 9px;margin-right:8px">TM</span>ToonMint</div>
                      <h1 style="font-size:24px;line-height:1.3;margin:26px 0 10px;color:#242136">%s</h1>
                      <p style="font-size:15px;line-height:1.6;color:#666273;margin:0">%s</p>
                    </td></tr>
                    <tr><td style="padding:22px 32px 8px">
                      <p style="font-size:12px;font-weight:700;letter-spacing:1.2px;text-transform:uppercase;color:#807b8e;margin:0 0 10px">Your verification code</p>
                      <div style="background:#f7f5fb;border:1px solid #e9e5f1;border-radius:12px;padding:18px;text-align:center;font-family:Consolas,Monaco,monospace;font-size:32px;letter-spacing:8px;font-weight:700;color:#302653;user-select:all;-webkit-user-select:all">%s</div>
                      <p style="font-size:14px;line-height:1.6;color:#666273;margin:16px 0 0">%s</p>
                      <p style="font-size:13px;line-height:1.6;color:#807b8e;margin:12px 0 0">This code expires in %d minutes and can only be used once.</p>
                    </td></tr>
                    <tr><td style="padding:22px 32px 30px"><div style="border-top:1px solid #eeeaf3;padding-top:18px;font-size:12px;line-height:1.6;color:#928da0">If you didn't request this code, you can safely ignore this email.<br>© ToonMint</div></td></tr>
                  </table>
                </body></html>
                """.formatted(title, intro, code, action, minutes);
    }
}
