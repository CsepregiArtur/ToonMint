package com.toonmint.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Cerere de trimitere email către utilizatori selectați sau adrese explicite.
 */
public record SendEmailRequest(
        List<Long> userIds,
        List<@Email String> emails,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 10000) String text) {
}
