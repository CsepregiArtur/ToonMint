package com.toonmint.service;

import com.toonmint.domain.CreditTransaction;
import com.toonmint.domain.CreditTransactionType;
import com.toonmint.domain.Job;
import com.toonmint.domain.User;
import com.toonmint.repository.CreditTransactionRepository;
import com.toonmint.repository.UserRepository;
import com.toonmint.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class CreditService {

    private final UserRepository userRepository;
    private final CreditTransactionRepository transactions;

    /**
     * Injectează repository-urile de utilizatori și de tranzacții.
     */
    public CreditService(UserRepository userRepository, CreditTransactionRepository transactions) {
        this.userRepository = userRepository;
        this.transactions = transactions;
    }

    /**
     * Blochează rândul utilizatorului pentru restul tranzacției curente, ca
     * generările concurente să nu poată depăși soldul disponibil.
     */
    public User lockForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> ApiException.notFound("User " + userId + " not found"));
    }

    /**
     * Aruncă 402 Payment Required dacă soldul este sub costul cerut.
     */
    public void ensureSufficientCredits(User user, int amount) {
        if (user.getCreditBalance() < amount) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "INSUFFICIENT_CREDITS",
                    "Not enough credits. Buy more credits to continue.",
                    Map.of("balance", user.getCreditBalance(), "required", amount,
                            "purchaseEndpoint", "/api/credits/purchase"));
        }
    }

    /**
     * Modifică entitatea deja blocată și scrie linia de audit. Tranzacția
     * aparține apelantului.
     */
    public CreditTransaction recordConsumption(User lockedUser, Job job, int amount) {
        lockedUser.setCreditBalance(lockedUser.getCreditBalance() - amount);
        CreditTransaction transaction = new CreditTransaction(
                lockedUser, -amount, CreditTransactionType.CONSUMPTION, job);
        transaction.setDescription("Generation for job #" + job.getId());
        return transactions.save(transaction);
    }

    /**
     * Rambursează o singură dată creditele unui job eșuat.
     */
    @Transactional
    public boolean refund(Job job, String reason) {
        if (job.getUser() == null || job.getCreditsConsumed() <= 0) {
            return false;
        }
        if (transactions.existsByReferenceJobIdAndType(job.getId(), CreditTransactionType.REFUND)) {
            return false;
        }
        User user = lockForUpdate(job.getUser().getId());
        user.setCreditBalance(user.getCreditBalance() + job.getCreditsConsumed());
        CreditTransaction transaction = new CreditTransaction(
                user, job.getCreditsConsumed(), CreditTransactionType.REFUND, job);
        transaction.setDescription("Refund for failed job #" + job.getId()
                + (reason == null || reason.isBlank() ? "" : ": " + truncate(reason)));
        transactions.save(transaction);
        return true;
    }

    /**
     * Adds credits (purchase, subscription grant, bonus). Idempotent when an
     * external reference is supplied.
     */
    /**
     * Adaugă credite (cumpărare, abonament, bonus) cu idempotență pe referința externă.
     */
    @Transactional
    public boolean grant(Long userId,
                         int amount,
                         CreditTransactionType type,
                         String provider,
                         String externalReference,
                         String description) {
        if (amount <= 0) {
            return false;
        }
        if (externalReference != null && transactions.existsByExternalReference(externalReference)) {
            return false;
        }
        User user = lockForUpdate(userId);
        user.setCreditBalance(user.getCreditBalance() + amount);
        CreditTransaction transaction = new CreditTransaction(user, amount, type, null);
        transaction.setProvider(provider);
        transaction.setExternalReference(externalReference);
        transaction.setDescription(description);
        transactions.save(transaction);
        return true;
    }

    /**
     * Soldul și tipul abonamentului unui utilizator.
     */
    public record Balance(int balance, String subscriptionType) {
    }

    /**
     * Limitează textul motivului pentru a încăpea în coloană.
     */
    private String truncate(String value) {
        return value.length() > 180 ? value.substring(0, 180) : value;
    }
}
