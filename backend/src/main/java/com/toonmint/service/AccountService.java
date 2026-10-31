package com.toonmint.service;

import com.toonmint.domain.CreditTransaction;
import com.toonmint.domain.Job;
import com.toonmint.domain.PurchaseAcknowledgement;
import com.toonmint.domain.User;
import com.toonmint.repository.CreditTransactionRepository;
import com.toonmint.repository.GuestTrialRepository;
import com.toonmint.repository.JobRepository;
import com.toonmint.repository.PurchaseAcknowledgementRepository;
import com.toonmint.repository.UserRepository;
import com.toonmint.repository.VerificationCodeRepository;
import com.toonmint.security.AuthenticatedUser;
import com.toonmint.web.dto.AccountExportResponse;
import com.toonmint.web.error.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Drepturile persoanei vizate care țin de cont: exportul datelor (GDPR art. 20) și
 * ștergerea contului (art. 17).
 *
 * <p>Ștergerea nu distruge rândul din `users`: îl anonimizează. Motivul este legal, nu
 * tehnic — documentele contabile (registrul de credite, tranzacțiile RevenueCat) trebuie
 * păstrate 10 ani (Legea 82/1991), iar ele sunt legate de utilizator prin cheie străină.
 * Datele de profil, creditele, joburile și codurile dispar; rămâne un identificator neutru.
 */
@Service
public class AccountService {

    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final CreditTransactionRepository creditTransactionRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final GuestTrialRepository guestTrialRepository;
    private final PurchaseAcknowledgementRepository acknowledgementRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository userRepository,
                          JobRepository jobRepository,
                          CreditTransactionRepository creditTransactionRepository,
                          VerificationCodeRepository verificationCodeRepository,
                          GuestTrialRepository guestTrialRepository,
                          PurchaseAcknowledgementRepository acknowledgementRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.creditTransactionRepository = creditTransactionRepository;
        this.verificationCodeRepository = verificationCodeRepository;
        this.guestTrialRepository = guestTrialRepository;
        this.acknowledgementRepository = acknowledgementRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Exportul complet al datelor deținute despre utilizator, ca JSON.
     */
    @Transactional(readOnly = true)
    public AccountExportResponse exportData(AuthenticatedUser currentUser) {
        User user = requireUser(currentUser.id());
        List<Job> jobs = jobRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
        List<CreditTransaction> transactions =
                creditTransactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        List<PurchaseAcknowledgement> acknowledgements =
                acknowledgementRepository.findByUserIdOrderByAcceptedAtDesc(user.getId());

        AccountExportResponse.Profile profile = new AccountExportResponse.Profile(
                user.getPublicId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreditBalance(),
                user.getSubscriptionType(),
                user.getSubscriptionExpiresAt(),
                user.getCreatedAt(),
                user.getTermsVersion(),
                user.getTermsAcceptedAt());

        List<AccountExportResponse.JobEntry> jobEntries = jobs.stream()
                .map(job -> new AccountExportResponse.JobEntry(
                        job.getPublicId(),
                        job.getStyleId(),
                        job.getStatus(),
                        job.getCreditsConsumed(),
                        job.getCreatedAt(),
                        job.getCompletedAt(),
                        job.getErrorMessage()))
                .toList();

        List<AccountExportResponse.TransactionEntry> transactionEntries = transactions.stream()
                .map(entry -> new AccountExportResponse.TransactionEntry(
                        entry.getId(),
                        entry.getAmount(),
                        entry.getType(),
                        entry.getProvider(),
                        entry.getDescription(),
                        entry.getCreatedAt()))
                .toList();

        List<AccountExportResponse.PurchaseEntry> purchaseEntries = acknowledgements.stream()
                .map(entry -> new AccountExportResponse.PurchaseEntry(
                        entry.getProductId(),
                        entry.getKind(),
                        entry.isImmediateDelivery(),
                        entry.isWithdrawalWaived(),
                        entry.getAcceptedAt()))
                .toList();

        return new AccountExportResponse(
                Instant.now(),
                "Images are never stored: only the metadata below exists in our systems.",
                profile,
                jobEntries,
                transactionEntries,
                purchaseEntries);
    }

    /**
     * Ștergerea (anonimizarea) contului. Operația este idempotentă.
     */
    @Transactional
    public void deleteAccount(AuthenticatedUser currentUser) {
        User user = requireUser(currentUser.id());
        if (user.isDeleted()) {
            return;
        }

        // Trialul gratuit este ținut pe email, separat de cont.
        guestTrialRepository.deleteByEmail(user.getEmail().toLowerCase(Locale.ROOT));
        guestTrialRepository.deleteByEmail(user.getEmail());

        verificationCodeRepository.deleteByUserId(user.getId());

        // Tranzacțiile rămân (obligație contabilă), dar fără referință către joburi.
        creditTransactionRepository.clearJobReferences(user.getId());
        jobRepository.deleteByUser_Id(user.getId());

        String anonymousEmail = "deleted-" + user.getPublicId() + "@deleted.invalid";
        String randomPassword = passwordEncoder.encode(UUID.randomUUID().toString());
        user.anonymize(anonymousEmail, randomPassword, Instant.now());
    }

    /**
     * Încarcă utilizatorul sau aruncă 404.
     */
    private User requireUser(Long userId) {
        if (userId == null) {
            throw ApiException.notFound("User not found");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User " + userId + " not found"));
    }
}
