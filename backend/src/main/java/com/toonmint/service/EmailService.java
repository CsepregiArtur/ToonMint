package com.toonmint.service;

import com.toonmint.config.AppProperties;
import com.toonmint.domain.EmailMessage;
import com.toonmint.domain.User;
import com.toonmint.repository.EmailMessageRepository;
import com.toonmint.repository.UserRepository;
import com.toonmint.web.dto.SendEmailRequest;
import com.toonmint.web.dto.SendEmailResponse;
import com.toonmint.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final int MAX_RECIPIENTS = 200;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final UserRepository userRepository;
    private final EmailMessageRepository emailMessages;
    private final AppProperties properties;

    /**
     * Injectează senderul de mail (opțional), repository-urile și configurația.
     */
    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        UserRepository userRepository,
                        EmailMessageRepository emailMessages,
                        AppProperties properties) {
        this.mailSenderProvider = mailSenderProvider;
        this.userRepository = userRepository;
        this.emailMessages = emailMessages;
        this.properties = properties;
    }

    /**
     * Trimite emailul către destinatarii selectați și înregistrează rezultatul.
     */
    @Transactional
    public SendEmailResponse send(SendEmailRequest request, Long sentByUserId) {
        Set<String> recipients = resolveRecipients(request);
        if (recipients.isEmpty()) {
            throw ApiException.badRequest("Select at least one recipient (userIds or emails)");
        }
        if (recipients.size() > MAX_RECIPIENTS) {
            throw ApiException.badRequest("At most " + MAX_RECIPIENTS + " recipients per request");
        }

        List<String> failures = new ArrayList<>();
        boolean simulated = !properties.mail().enabled();
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        if (simulated || mailSender == null) {
            log.info("Mail delivery disabled (app.mail.enabled=false). Simulated email '{}' to {} recipients",
                    request.subject(), recipients.size());
        } else {
            String from = properties.mail().fromName() == null || properties.mail().fromName().isBlank()
                    ? properties.mail().from()
                    : properties.mail().fromName() + " <" + properties.mail().from() + ">";
            for (String recipient : recipients) {
                try {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setFrom(from);
                    message.setTo(recipient);
                    message.setSubject(request.subject());
                    message.setText(request.text());
                    mailSender.send(message);
                } catch (RuntimeException ex) {
                    log.warn("Could not send email to {}: {}", recipient, ex.getMessage());
                    failures.add(recipient + ": " + ex.getMessage());
                }
            }
        }

        int sent = recipients.size() - failures.size();
        String status = simulated ? "SIMULATED" : failures.isEmpty() ? "SENT" : sent == 0 ? "FAILED" : "PARTIAL";
        User sentBy = sentByUserId == null ? null : userRepository.findById(sentByUserId).orElse(null);
        emailMessages.save(new EmailMessage(sentBy, recipients.size(), request.subject(), request.text(),
                status, failures.isEmpty() ? null : String.join("; ", failures)));

        return new SendEmailResponse(recipients.size(), sent, failures.size(), simulated, failures);
    }

    /**
     * Combină id-urile de utilizatori cu adresele introduse manual, fără duplicate.
     */
    private Set<String> resolveRecipients(SendEmailRequest request) {
        Set<String> recipients = new LinkedHashSet<>();
        if (request.emails() != null) {
            request.emails().stream()
                    .filter(email -> email != null && !email.isBlank())
                    .map(email -> email.trim().toLowerCase())
                    .forEach(recipients::add);
        }
        if (request.userIds() != null && !request.userIds().isEmpty()) {
            userRepository.findAllById(request.userIds())
                    .forEach(user -> recipients.add(user.getEmail()));
        }
        return recipients;
    }
}
