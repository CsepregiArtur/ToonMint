package com.toonmint.web;

import com.toonmint.security.CurrentUser;
import com.toonmint.service.EmailService;
import com.toonmint.service.UserService;
import com.toonmint.repository.SubscriptionEventRepository;
import com.toonmint.web.dto.PageResponse;
import com.toonmint.web.dto.SendEmailRequest;
import com.toonmint.web.dto.SendEmailResponse;
import com.toonmint.web.dto.SubscriptionEventResponse;
import com.toonmint.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin")
public class AdminController {

    private final EmailService emailService;
    private final UserService userService;
    private final SubscriptionEventRepository subscriptionEvents;
    private final CurrentUser currentUser;

    /**
     * Injectează serviciile de email, utilizatori și evenimente.
     */
    public AdminController(EmailService emailService,
                           UserService userService,
                           SubscriptionEventRepository subscriptionEvents,
                           CurrentUser currentUser) {
        this.emailService = emailService;
        this.userService = userService;
        this.subscriptionEvents = subscriptionEvents;
        this.currentUser = currentUser;
    }

    /**
     * Trimite un email către utilizatorii selectați.
     */
    @PostMapping("/send-email")
    @Operation(summary = "Send an email to selected users (Gmail SMTP)")
    public SendEmailResponse sendEmail(@Valid @RequestBody SendEmailRequest request) {
        return emailService.send(request, currentUser.require().id());
    }

    /**
     * Listează utilizatorii pentru consola de admin.
     */
    @GetMapping("/users")
    @Operation(summary = "List users for the operator console")
    public PageResponse<UserResponse> users(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return userService.list(page, size);
    }

    /**
     * Listează ultimele webhook-uri RevenueCat.
     */
    @GetMapping("/revenuecat-events")
    @Operation(summary = "Recent RevenueCat webhook events")
    public PageResponse<SubscriptionEventResponse> revenueCatEvents(@RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        return PageResponse.from(subscriptionEvents.findAllByOrderByReceivedAtDesc(pageable),
                event -> new SubscriptionEventResponse(
                        event.getId(),
                        event.getEventId(),
                        event.getEventType(),
                        event.getProductId(),
                        event.getStatus(),
                        event.getMessage(),
                        event.getReceivedAt()));
    }
}
