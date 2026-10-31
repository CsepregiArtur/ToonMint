package com.toonmint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toonmint.domain.SubscriptionType;
import com.toonmint.domain.User;
import com.toonmint.repository.CreditTransactionRepository;
import com.toonmint.repository.JobRepository;
import com.toonmint.repository.SubscriptionEventRepository;
import com.toonmint.repository.UserRepository;
import com.toonmint.storage.TempImageStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ApiTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JobRepository jobRepository;

    @Autowired
    protected CreditTransactionRepository creditTransactionRepository;

    @Autowired
    protected SubscriptionEventRepository subscriptionEventRepository;

    @Autowired
    protected TempImageStore tempImageStore;

    @Autowired
    protected TestVerificationMailSender verificationMail;

    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    protected void registerPending(String email) throws Exception {
        String body = objectMapper.writeValueAsString(new RegisterPayload(email, "sup3r-secret-pass", "Test user"));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    protected String verifyEmailAndGetToken(String email, String code) throws Exception {
        String body = objectMapper.writeValueAsString(new VerifyPayload(email, code));
        String response = mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("token").asText();
    }

    protected String registerAndGetToken(String email) throws Exception {
        registerPending(email);
        return verifyEmailAndGetToken(email, verificationMail.code(email));
    }

    @Transactional
    protected void giveCredits(String email, int credits) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setCreditBalance(credits);
        userRepository.save(user);
    }

    @Transactional
    protected void giveSubscription(String email, SubscriptionType type, Instant expiresAt) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setSubscriptionType(type);
        user.setSubscriptionExpiresAt(expiresAt);
        userRepository.save(user);
    }

    @Transactional
    protected void promoteToAdmin(String email) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setRole(com.toonmint.domain.UserRole.ADMIN);
        userRepository.save(user);
    }

    @Transactional
    protected User userByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }

    protected byte[] pngPhoto(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setPaint(new java.awt.GradientPaint(0, 0, Color.ORANGE, width, height, Color.BLUE));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    protected JsonNode awaitJobStatus(String token, long jobId, String expected, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        JsonNode last = null;
        while (Instant.now().isBefore(deadline)) {
            String body = mockMvc.perform(get("/api/jobs/" + jobId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            last = objectMapper.readTree(body);
            if (expected.equals(last.path("status").asText())) {
                return last;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Job " + jobId + " did not reach " + expected + ", last state: " + last);
    }

    protected JsonNode awaitGuestJob(String publicId, String expected, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        JsonNode last = null;
        while (Instant.now().isBefore(deadline)) {
            String body = mockMvc.perform(get("/api/public/jobs/" + publicId))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            last = objectMapper.readTree(body);
            if (expected.equals(last.path("status").asText())) {
                return last;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Guest job " + publicId + " did not reach " + expected + ", last: " + last);
    }

    protected record RegisterPayload(String email, String password, String displayName,
                                     boolean acceptedTerms, String termsVersion) {

        /**
         * Varianta folosită de majoritatea testelor: acceptă termenii curent.
         */
        RegisterPayload(String email, String password, String displayName) {
            this(email, password, displayName, true, "2026-10-31");
        }
    }

    protected record VerifyPayload(String email, String code) {
    }
}
