package com.toonmint.service;

import com.toonmint.domain.User;
import com.toonmint.repository.UserRepository;
import com.toonmint.security.AuthenticatedUser;
import com.toonmint.web.dto.PageResponse;
import com.toonmint.web.dto.UpdateUserRequest;
import com.toonmint.web.dto.UserResponse;
import com.toonmint.web.error.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;

    /**
     * Injectează repository-ul de utilizatori.
     */
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returnează profilul unui utilizator dacă solicitantul este proprietarul sau operator.
     */
    @Transactional(readOnly = true)
    public UserResponse get(Long userId, AuthenticatedUser currentUser) {
        assertSelfOrAdmin(userId, currentUser);
        return UserResponse.from(requireUser(userId), Instant.now());
    }

    /**
     * Caută un utilizator după identificatorul public.
     */
    @Transactional(readOnly = true)
    public UserResponse getByPublicId(String publicId) {
        return userRepository.findByPublicId(java.util.UUID.fromString(publicId))
                .map(user -> UserResponse.from(user, Instant.now()))
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    /**
     * Actualizează numele afișat și/sau emailul, cu verificarea unicității.
     */
    @Transactional
    public UserResponse update(Long userId, UpdateUserRequest request, AuthenticatedUser currentUser) {
        assertSelfOrAdmin(userId, currentUser);
        User user = requireUser(userId);

        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
                throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
            }
            user.setEmail(email);
        }
        return UserResponse.from(user, Instant.now());
    }

    /**
     * Listează utilizatorii pentru consola de administrare.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        return PageResponse.from(userRepository.findAll(pageable),
                user -> UserResponse.from(user, Instant.now()));
    }

    /**
     * Încarcă utilizatorul sau aruncă 404.
     */
    public User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User " + userId + " not found"));
    }

    /**
     * Permite accesul doar proprietarului sau unui operator.
     */
    private void assertSelfOrAdmin(Long userId, AuthenticatedUser currentUser) {
        if (!userId.equals(currentUser.id()) && !currentUser.isAdmin()) {
            throw ApiException.forbidden("You can only access your own profile");
        }
    }
}
