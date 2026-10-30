package com.toonmint.web.dto;

import com.toonmint.domain.SubscriptionType;
import com.toonmint.domain.User;
import com.toonmint.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Profilul public al unui utilizator, fără date sensibile.
 */
public record UserResponse(
        Long id,
        UUID publicId,
        String email,
        String displayName,
        int creditBalance,
        SubscriptionType subscriptionType,
        boolean subscriptionActive,
        Instant subscriptionExpiresAt,
        UserRole role,
        Instant createdAt) {

    /**
     * Construiește DTO-ul din entitate și calculează starea abonamentului.
     */
    public static UserResponse from(User user, Instant now) {
        return new UserResponse(
                user.getId(),
                user.getPublicId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreditBalance(),
                user.getSubscriptionType(),
                user.isSubscriptionActive(now),
                user.getSubscriptionExpiresAt(),
                user.getRole(),
                user.getCreatedAt());
    }
}
