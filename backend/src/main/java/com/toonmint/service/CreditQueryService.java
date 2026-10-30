package com.toonmint.service;

import com.toonmint.domain.CreditTransaction;
import com.toonmint.repository.CreditTransactionRepository;
import com.toonmint.security.AuthenticatedUser;
import com.toonmint.web.dto.PageResponse;
import com.toonmint.web.dto.TransactionResponse;
import com.toonmint.web.error.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditQueryService {

    private final CreditTransactionRepository transactions;
    /**
     * Injectează repository-ul de tranzacții.
     */
    public CreditQueryService(CreditTransactionRepository transactions) {
        this.transactions = transactions;
    }

    /**
     * Returnează istoricul de credite al unui utilizator (self sau operator).
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> history(Long userId, int page, int size, AuthenticatedUser currentUser) {
        if (!userId.equals(currentUser.id()) && !currentUser.isAdmin()) {
            throw ApiException.forbidden("You can only read your own transactions");
        }
        var pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        return PageResponse.from(transactions.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                CreditQueryService::toResponse);
    }

    /**
     * Mapează entitatea de tranzacție la DTO-ul public.
     */
    private static TransactionResponse toResponse(CreditTransaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getReferenceJob() == null ? null : transaction.getReferenceJob().getId(),
                transaction.getProvider(),
                transaction.getDescription(),
                transaction.getCreatedAt());
    }
}
