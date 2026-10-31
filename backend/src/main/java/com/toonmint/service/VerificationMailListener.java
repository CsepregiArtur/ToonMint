package com.toonmint.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Trimite emailul cu codul OTP numai după commit-ul tranzacției care l-a emis.
 */
@Service
public class VerificationMailListener {

    private final VerificationMailSender mailSender;

    /**
     * Injectează livrarea codurilor.
     */
    public VerificationMailListener(VerificationMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Listener declanșat după commit.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCodeIssued(VerificationCodeIssuedEvent event) {
        mailSender.send(event.email(), event.code(), event.purpose(), event.expiresAt());
    }
}
