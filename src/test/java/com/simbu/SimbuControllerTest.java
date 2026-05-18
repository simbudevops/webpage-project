package com.simbu;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SimbuControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ── Test 1: GET / redirects ───────────────────────────────────────────
    @Test
    public void testHomeRedirect() throws Exception {
        mockMvc.perform(get("/"))
               .andExpect(status().is3xxRedirection());
    }

    // ── Test 2: GET /health returns 200 OK ───────────────────────────────
    @Test
    public void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
               .andExpect(status().isOk())
               .andExpect(content().string("OK"));
    }

    // ── Test 3: POST /api/verify with empty keys returns 400 ─────────────
    @Test
    public void testVerifyEmptyKeys() throws Exception {
        String body = "{\"accountId\":\"123456789012\",\"username\":\"test\",\"accessKey\":\"\",\"secretKey\":\"\"}";
        mockMvc.perform(post("/api/verify")
               .contentType(MediaType.APPLICATION_JSON)
               .content(body))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.status").value("error"));
    }

    // ── Test 4: POST /api/verify with invalid key format returns 400 ──────
    @Test
    public void testVerifyInvalidKeyFormat() throws Exception {
        String body = "{\"accountId\":\"\",\"username\":\"\",\"accessKey\":\"BADKEY123\",\"secretKey\":\"somesecret\"}";
        mockMvc.perform(post("/api/verify")
               .contentType(MediaType.APPLICATION_JSON)
               .content(body))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.status").value("error"))
               .andExpect(jsonPath("$.message").value(
                   "Invalid Access Key format. Must start with AKIA or ASIA."));
    }

    // ── Test 5: POST /api/verify with fake AKIA key -> AWS rejects -> 401 ─
    @Test
    public void testVerifyFakeAkiaKey() throws Exception {
        String body = "{\"accountId\":\"\",\"username\":\"\",\"accessKey\":\"AKIAIOSFODNN7EXAMPLE\",\"secretKey\":\"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY\"}";
        mockMvc.perform(post("/api/verify")
               .contentType(MediaType.APPLICATION_JSON)
               .content(body))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.status").value("error"));
    }
}
