package com.toonmint;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * `app.demo.enabled=false` trebuie să ascundă endpointul și să nu mai creeze
 * conturile demo, indiferent de ce a fost coapte la build (Spring AOT).
 */
@SpringBootTest(properties = "app.demo.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoLoginDisabledApiTest extends ApiTestSupport {

    @Test
    void availabilityIsHiddenWhenDisabled() throws Exception {
        mockMvc.perform(get("/api/auth/demo-login"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEMO_DISABLED"));
    }

    @Test
    void loginIsHiddenWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/auth/demo-login")
                        .contentType("application/json")
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEMO_DISABLED"));
    }
}
