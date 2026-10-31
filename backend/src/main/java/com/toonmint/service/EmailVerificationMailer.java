package com.toonmint.service;

import com.toonmint.config.AppProperties;
import com.toonmint.domain.VerificationPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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
        String text = bodyFor(purpose, code, expiresAt);
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!properties.mail().enabled() || mailSender == null) {
            log.warn("Mail disabled (app.mail.enabled=false). {} code for {} is {} (valid until {})",
                    purpose, email, code, expiresAt);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String fromName = properties.mail().fromName();
            message.setFrom(fromName == null || fromName.isBlank()
                    ? properties.mail().from()
                    : fromName + " <" + properties.mail().from() + ">");
            message.setTo(email);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Sent {} code to {}", purpose, email);
        } catch (RuntimeException ex) {
            log.warn("Could not send {} email to {}: {}", purpose, email, ex.getMessage());
        }
    }

    /**
     * Subiectul emailului, în funcție de scopul codului.
     */
    private static String subjectFor(VerificationPurpose purpose) {
        return purpose == VerificationPurpose.PASSWORD_RESET
                ? "Resetarea parolei ToonMint"
                : "Codul de verificare ToonMint";
    }

    /**
     * Corpul emailului, cu codul și durata de valabilitate.
     */
    private static String bodyFor(VerificationPurpose purpose, String code, Instant expiresAt) {
        long minutes = Math.max(1, Duration.between(Instant.now(), expiresAt).toMinutes());
        String intro = purpose == VerificationPurpose.PASSWORD_RESET
                ? "Ai cerut resetarea parolei contului tău ToonMint."
                : "Bun venit în ToonMint! Confirmă-ți adresa de email pentru a activa contul.";
        String action = purpose == VerificationPurpose.PASSWORD_RESET
                ? "Introdu codul în pagina de resetare a parolei, împreună cu parola nouă."
                : "Introdu codul în pagina de verificare pentru a-ți activa contul.";
        return intro + "\n\n"
                + "Codul tău: " + code + "\n\n"
                + action + "\n"
                + "Codul expiră în " + minutes + " minute și poate fi folosit o singură dată.\n\n"
                + "Dacă nu ai cerut tu acest cod, ignoră acest email.";
    }
}
