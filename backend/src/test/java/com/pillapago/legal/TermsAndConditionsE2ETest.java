package com.pillapago.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillapago.auth.dto.RegisterRequest;
import com.pillapago.tenant.domain.User;
import com.pillapago.tenant.repository.UserRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TermsAndConditionsE2ETest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    @Test
    @DisplayName("01. Consulta pública de Términos y Condiciones: 200 OK con cláusulas y PCI-DSS")
    void testGetActiveLegalTerms() throws Exception {
        mockMvc.perform(get("/v1/legal/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1.0"))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.pciDssCompliance.panStored").value(false))
                .andExpect(jsonPath("$.pciDssCompliance.cvvStored").value(false))
                .andExpect(jsonPath("$.pciDssCompliance.encryptionStandard").value("AES-256-GCM (Google Tink)"))
                .andExpect(jsonPath("$.clauses").isArray())
                .andExpect(jsonPath("$.clauses.length()").value(Matchers.greaterThanOrEqualTo(5)));
    }

    @Test
    @DisplayName("02. Registro rechazado con 400 Bad Request si acceptedTerms es false")
    void testRegisterRejectedWhenTermsNotAccepted() throws Exception {
        String email = "noterms_" + UUID.randomUUID().toString().substring(0, 8) + "@comercio.pe";
        RegisterRequest req = RegisterRequest.builder()
                .merchantName("Comercio Sin TyC")
                .adminName("Admin Rechazado")
                .email(email)
                .password("Password123#Secure")
                .confirmPassword("Password123#Secure")
                .acceptedTerms(false) // No aceptó
                .build();

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", Matchers.containsString(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.invalidParams.acceptedTerms").value(
                        Matchers.containsString("Debes aceptar los Términos y Condiciones")));
    }

    @Test
    @DisplayName("03. Registro exitoso con acceptedTerms=true persiste timestamp y versión legal")
    void testRegisterPersistsTermsConsent() throws Exception {
        String email = "withterms_" + UUID.randomUUID().toString().substring(0, 8) + "@comercio.pe";
        RegisterRequest req = RegisterRequest.builder()
                .merchantName("Comercio Con TyC")
                .adminName("Admin Conforme")
                .email(email)
                .password("Password123#Secure")
                .confirmPassword("Password123#Secure")
                .acceptedTerms(true)
                .build();

        mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));

        User savedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(savedUser.getTermsAcceptedAt()).isNotNull();
        assertThat(savedUser.getTermsVersion()).isEqualTo("v1.0");
    }
}
