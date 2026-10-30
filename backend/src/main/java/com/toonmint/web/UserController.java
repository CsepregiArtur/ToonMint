package com.toonmint.web;

import com.toonmint.domain.JobStatus;
import com.toonmint.service.AuthService;
import com.toonmint.service.JobService;
import com.toonmint.service.UserService;
import com.toonmint.security.AuthenticatedUser;
import com.toonmint.security.CurrentUser;
import com.toonmint.web.dto.JobResponse;
import com.toonmint.web.dto.PageResponse;
import com.toonmint.web.dto.RegisterRequest;
import com.toonmint.web.dto.UpdateUserRequest;
import com.toonmint.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;
    private final AuthService authService;
    private final JobService jobService;
    private final CurrentUser currentUser;

    /**
     * Injectează serviciile de utilizatori, autentificare și job-uri.
     */
    public UserController(UserService userService, AuthService authService, JobService jobService,
                          CurrentUser currentUser) {
        this.userService = userService;
        this.authService = authService;
        this.jobService = jobService;
        this.currentUser = currentUser;
    }

    /**
     * Creează un utilizator prin endpoint-ul public de înregistrare.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user (public sign-up)")
    public UserResponse create(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(authService.createUser(request), Instant.now());
    }

    /**
     * Returnează profilul utilizatorului autentificat.
     */
    @GetMapping("/me")
    @Operation(summary = "Current user profile")
    public UserResponse me() {
        AuthenticatedUser user = currentUser.require();
        return userService.get(user.id(), user);
    }

    /**
     * Returnează detaliile unui utilizator (proprietar sau operator).
     */
    @GetMapping("/{id}")
    @Operation(summary = "User details (self or operator)")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id, currentUser.require());
    }

    /**
     * Actualizează profilul unui utilizator (proprietar sau operator).
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update profile (self or operator)")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request, currentUser.require());
    }

    /**
     * Listează job-urile unui utilizator, opțional filtrate după status.
     */
    @GetMapping("/{userId}/jobs")
    @Operation(summary = "Jobs of a user, optionally filtered by status (custom query)")
    public PageResponse<JobResponse> jobs(@PathVariable Long userId,
                                          @RequestParam(required = false) JobStatus status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return jobService.listUserJobs(userId, status, page, size, currentUser.require());
    }
}
