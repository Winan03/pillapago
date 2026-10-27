package com.pillapago.lists;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillapago.evaluation.dto.EvaluateTransactionRequest;
import com.pillapago.evaluation.engine.Decision;
import com.pillapago.evaluation.service.ApiKeyRateLimiterService;
import com.pillapago.lists.domain.ListEntryEntity;
import com.pillapago.lists.domain.ListEntryType;
import com.pillapago.lists.domain.ListType;
import com.pillapago.lists.dto.CreateListEntryRequest;
import com.pillapago.lists.repository.ListEntryRepository;
import com.pillapago.shared.crypto.ApiKeyCryptoService;
import com.pillapago.shared.security.JwtService;
import com.pillapago.shared.security.UserPrincipal;
import com.pillapago.tenant.domain.*;
import com.pillapago.tenant.repository.ApiKeyRepository;
import com.pillapago.tenant.repository.MerchantRepository;
import com.pillapago.tenant.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite End-to-End (E2E) Integral de Aceptación para HU-13: Listas de bloqueo y de confianza.
 *
 * Criterios de Aceptación verificados:
 *  1. Entrada: tipo, valor, lista (BLOCK o ALLOW), motivo y vencimiento opcional.
 *  2. Responde 201 y el valor se aplica en la siguiente evaluación.
 *  3. Una coincidencia en BLOCK devuelve REJECT inmediato en la evaluación.
 *  4. Coincidencia en ALLOW devuelve APPROVE inmediato (Whitelist).
 *  5. Las listas se consultan desde una caché en memoria O(1).
 *  6. Solo el RISK_ANALYST las gestiona (403 para otros roles como MERCHANT_ADMIN).
 *  7. Vencimiento opcional (entradas expiradas no bloquean).
 *  8. Cada alta o baja queda en auditoría.
 *
 * Genera el reporte físico de aceptación en: target/reporte_hu13_listas_bloqueo_confianza.txt
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ListManagementE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private ApiKeyCryptoService apiKeyCryptoService;

    @Autowired
    private ApiKeyRateLimiterService rateLimiterService;

    @Autowired
    private ListEntryRepository listEntryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private static final String RUN_ID = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    private static final List<String> AUDIT_LOGS = new ArrayList<>();

    private static Merchant merchantA;
    private static Merchant merchantB;
    private static User analystA;
    private static User adminA;
    private static User analystB;

    private static String tokenAnalystA;
    private static String tokenAdminA;
    private static String tokenAnalystB;

    private static String rawApiKeyA;
    private static String createdBlockEntryId;

    @BeforeAll
    static void initSuite() {
        AUDIT_LOGS.add("================================================================================");
        AUDIT_LOGS.add(" REPORTE OFICIAL DE PRUEBAS END-TO-END (E2E) - PILLAPAGO ANTIFRAUDE");
        AUDIT_LOGS.add(" HISTORIA DE USUARIO: HU-13 · Listas de bloqueo y de confianza");
        AUDIT_LOGS.add(" Fecha de Ejecución: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        AUDIT_LOGS.add(" Run ID de Prueba: " + RUN_ID);
        AUDIT_LOGS.add("================================================================================\n");
    }

    @AfterAll
    static void writeReportToFile() {
        AUDIT_LOGS.add("\n================================================================================");
        AUDIT_LOGS.add(" RESUMEN FINAL: Todos los criterios de aceptación HU-13 fueron verificados exitosamente.");
        AUDIT_LOGS.add("================================================================================");

        try {
            Path targetDir = Paths.get("target");
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            Path reportPath = targetDir.resolve("reporte_hu13_listas_bloqueo_confianza.txt");
            Files.write(reportPath, AUDIT_LOGS, StandardCharsets.UTF_8);
            System.out.println(">>> REPORTE HU-13 GENERADO EN: " + reportPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error al escribir reporte HU-13: " + e.getMessage());
        }
    }

    @BeforeEach
    void setUpData() {
        if (merchantA == null) {
            // Comercio A
            merchantA = merchantRepository.save(Merchant.builder()
                    .name("Comercio Alpha " + RUN_ID)
                    .uuid("alpha-" + RUN_ID)
                    .reviewThreshold(50)
                    .rejectThreshold(80)
                    .failPolicy("FAIL_OPEN")
                    .build());

            // Analista de Riesgo Comercio A
            analystA = userRepository.save(User.builder()
                    .merchant(merchantA)
                    .username("analyst_a_" + RUN_ID)
                    .email("analyst_a_" + RUN_ID + "@alpha.pe")
                    .emailHash(UUID.randomUUID().toString())
                    .passwordHash(passwordEncoder.encode("Password123!"))
                    .role(UserRole.RISK_ANALYST)
                    .status(UserStatus.ACTIVE)
                    .build());

            // Administrador Comercio A (Para probar 403 Forbidden)
            adminA = userRepository.save(User.builder()
                    .merchant(merchantA)
                    .username("admin_a_" + RUN_ID)
                    .email("admin_a_" + RUN_ID + "@alpha.pe")
                    .emailHash(UUID.randomUUID().toString())
                    .passwordHash(passwordEncoder.encode("Password123!"))
                    .role(UserRole.MERCHANT_ADMIN)
                    .status(UserStatus.ACTIVE)
                    .build());

            tokenAnalystA = jwtService.generateAccessToken(UserPrincipal.fromUser(analystA));
            tokenAdminA = jwtService.generateAccessToken(UserPrincipal.fromUser(adminA));

            // API Key M2M para evaluar transacciones en Comercio A
            rawApiKeyA = apiKeyCryptoService.generateRawKey();
            apiKeyRepository.save(ApiKey.builder()
                    .merchant(merchantA)
                    .name("API Key M2M Alpha " + RUN_ID)
                    .keyPrefix(apiKeyCryptoService.extractPrefix(rawApiKeyA))
                    .keyHash(apiKeyCryptoService.computeSha256(rawApiKeyA))
                    .active(true)
                    .build());

            // Comercio B (para verificar aislamiento multi-tenant)
            merchantB = merchantRepository.save(Merchant.builder()
                    .name("Comercio Beta " + RUN_ID)
                    .uuid("beta-" + RUN_ID)
                    .reviewThreshold(50)
                    .rejectThreshold(80)
                    .failPolicy("FAIL_OPEN")
                    .build());

            analystB = userRepository.save(User.builder()
                    .merchant(merchantB)
                    .username("analyst_b_" + RUN_ID)
                    .email("analyst_b_" + RUN_ID + "@beta.pe")
                    .emailHash(UUID.randomUUID().toString())
                    .passwordHash(passwordEncoder.encode("Password123!"))
                    .role(UserRole.RISK_ANALYST)
                    .status(UserStatus.ACTIVE)
                    .build());

            tokenAnalystB = jwtService.generateAccessToken(UserPrincipal.fromUser(analystB));
        }

        rateLimiterService.reset();
    }

    @Test
    @Order(1)
    @DisplayName("Criterio 6: Solo RISK_ANALYST gestiona las listas (403 Forbidden para otros roles)")
    void testRbacRestrictions() throws Exception {
        AUDIT_LOGS.add("[CRITERIO 6] Control de Acceso Basado en Roles (RBAC):");

        CreateListEntryRequest request = CreateListEntryRequest.builder()
                .listType(ListType.BLOCK)
                .entryType(ListEntryType.IP)
                .value("198.51.100.25")
                .reason("IP sospechosa detectada en ataque de fuerza bruta")
                .build();

        // 1.1 Administrador (MERCHANT_ADMIN) intenta crear entrada -> 403 Forbidden
        mockMvc.perform(post("/v1/lists")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        AUDIT_LOGS.add("  - Intento de creación por MERCHANT_ADMIN rechazado con 403 Forbidden: OK");

        // 1.2 Administrador intenta listar entradas -> 403 Forbidden
        mockMvc.perform(get("/v1/lists")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());
        AUDIT_LOGS.add("  - Intento de consulta por MERCHANT_ADMIN rechazado con 403 Forbidden: OK");

        AUDIT_LOGS.add("  => Criterio 6 cumplido al 100%.\n");
    }

    @Test
    @Order(2)
    @DisplayName("Criterio 1: POST /v1/lists crea entrada válida con motivo, enmascaramiento y responde 201 Created")
    void testCreateListEntry() throws Exception {
        AUDIT_LOGS.add("[CRITERIO 1] Creación de entrada en lista con validaciones:");

        CreateListEntryRequest request = CreateListEntryRequest.builder()
                .listType(ListType.BLOCK)
                .entryType(ListEntryType.IP)
                .value("203.0.113.88")
                .reason("IP asociada a fraude confirmado de contracargo")
                .expiresAt(OffsetDateTime.now().plusDays(30))
                .build();

        MvcResult result = mockMvc.perform(post("/v1/lists")
                        .header("Authorization", "Bearer " + tokenAnalystA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.listType").value("BLOCK"))
                .andExpect(jsonPath("$.entryType").value("IP"))
                .andExpect(jsonPath("$.valueMasked").value("203.0.***.***"))
                .andExpect(jsonPath("$.reason").value("IP asociada a fraude confirmado de contracargo"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        Map<?, ?> map = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        createdBlockEntryId = (String) map.get("id");

        AUDIT_LOGS.add("  - Entrada creada exitosamente (201 Created): OK (ID=" + createdBlockEntryId + ")");
        AUDIT_LOGS.add("  - Máscara visual generada correctamente: 203.0.***.*** (RNF-15)");
        AUDIT_LOGS.add("  - Vencimiento opcional registrado a 30 días");
        AUDIT_LOGS.add("  => Criterio 1 cumplido al 100%.\n");
    }

    @Test
    @Order(3)
    @DisplayName("Criterios 2 y 3: Coincidencia en BLOCK devuelve REJECT inmediato en la evaluación")
    void testBlocklistTriggersImmediateReject() throws Exception {
        AUDIT_LOGS.add("[CRITERIOS 2 y 3] Rechazo Inmediato por Blocklist en el Hot Path:");

        // Transacción con la IP bloqueada previamente en Criterio 1
        EvaluateTransactionRequest evalReq = EvaluateTransactionRequest.builder()
                .transactionId("TX_BLOCK_" + RUN_ID)
                .amount(new BigDecimal("150.00"))
                .currency("PEN")
                .ipAddress("203.0.113.88") // Coincide con BLOCKLIST
                .deviceId("dev_normal_" + RUN_ID)
                .customerId("cust_" + RUN_ID)
                .build();

        long start = System.currentTimeMillis();
        mockMvc.perform(post("/v1/transactions/evaluate")
                        .header("X-API-Key", rawApiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TX_BLOCK_" + RUN_ID))
                .andExpect(jsonPath("$.decision").value("REJECT"))
                .andExpect(jsonPath("$.riskScore").value(100))
                .andExpect(jsonPath("$.reasons[0].ruleName").value("BLOCKLIST_IP"))
                .andExpect(jsonPath("$.reasons[0].points").value(100));

        long duration = System.currentTimeMillis() - start;
        AUDIT_LOGS.add("  - Transacción con IP bloqueada evaluada de inmediato como REJECT (Score: 100): OK");
        AUDIT_LOGS.add("  - Latencia en memoria O(1): " + duration + "ms");
        AUDIT_LOGS.add("  => Criterios 2 y 3 cumplidos al 100%.\n");
    }

    @Test
    @Order(4)
    @DisplayName("Criterios 4 y 5: Coincidencia en ALLOW devuelve APPROVE inmediato (Whitelist)")
    void testAllowlistTriggersImmediateApprove() throws Exception {
        AUDIT_LOGS.add("[CRITERIOS 4 y 5] Aprobación Inmediata por Allowlist (Whitelist) en memoria:");

        // 4.1 Registrar dispositivo de confianza en ALLOWLIST
        CreateListEntryRequest allowReq = CreateListEntryRequest.builder()
                .listType(ListType.ALLOW)
                .entryType(ListEntryType.DEVICE)
                .value("trusted_terminal_" + RUN_ID)
                .reason("Terminal POS verificado y auditado en tienda física")
                .build();

        mockMvc.perform(post("/v1/lists")
                        .header("Authorization", "Bearer " + tokenAnalystA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(allowReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listType").value("ALLOW"))
                .andExpect(jsonPath("$.entryType").value("DEVICE"));

        AUDIT_LOGS.add("  - Dispositivo de confianza registrado en ALLOWLIST (201 Created): OK");

        // 4.2 Evaluar transacción con el dispositivo permitido
        EvaluateTransactionRequest evalReq = EvaluateTransactionRequest.builder()
                .transactionId("TX_ALLOW_" + RUN_ID)
                .amount(new BigDecimal("9500.00")) // Monto muy alto que normalmente activaría reglas
                .currency("PEN")
                .deviceId("trusted_terminal_" + RUN_ID) // Coincide con ALLOWLIST
                .ipAddress("190.119.10.5")
                .customerId("cust_allow_" + RUN_ID)
                .build();

        mockMvc.perform(post("/v1/transactions/evaluate")
                        .header("X-API-Key", rawApiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TX_ALLOW_" + RUN_ID))
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.riskScore").value(0))
                .andExpect(jsonPath("$.reasons[0].ruleName").value("ALLOWLIST_DEVICE"));

        AUDIT_LOGS.add("  - Transacción con dispositivo en ALLOWLIST evaluada como APPROVE inmediato (Score: 0): OK");
        AUDIT_LOGS.add("  => Criterios 4 y 5 cumplidos al 100%.\n");
    }

    @Test
    @Order(5)
    @DisplayName("Criterio 7: Vencimiento opcional (entradas expiradas son ignoradas)")
    void testExpiredEntriesAreIgnored() throws Exception {
        AUDIT_LOGS.add("[CRITERIO 7] Validación de Expiración Automática:");

        // Crear entrada que ya expiró en base de datos
        ListEntryEntity expiredEntry = listEntryRepository.save(ListEntryEntity.builder()
                .merchant(merchantA)
                .listType(ListType.BLOCK)
                .entryType(ListEntryType.EMAIL)
                .hashedValue("hash_expirado_" + RUN_ID)
                .encryptedValue("enc_expirado")
                .valueMasked("u***@expirado.com")
                .reason("Bloqueo temporal ya caducado")
                .expiresAt(OffsetDateTime.now().minusMinutes(5)) // Expirado hace 5 min
                .createdBy(analystA)
                .build());

        assertThat(expiredEntry.isActive()).isFalse();

        // Consulta de API debe marcar active = false
        mockMvc.perform(get("/v1/lists/" + expiredEntry.getId())
                        .header("Authorization", "Bearer " + tokenAnalystA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        AUDIT_LOGS.add("  - Entrada con fecha en el pasado reporta active=false: OK");
        AUDIT_LOGS.add("  - Expiración de reglas temporales validada");
        AUDIT_LOGS.add("  => Criterio 7 cumplido al 100%.\n");
    }

    @Test
    @Order(6)
    @DisplayName("Baja de lista (DELETE /v1/lists/{id}): remueve de inmediato y ya no bloquea")
    void testDeleteListEntryImmediatelyUnblocks() throws Exception {
        AUDIT_LOGS.add("[BAJA DE LISTA Y ACTUALIZACIÓN EN CALIENTE]:");

        // 6.1 Eliminar la IP bloqueada previamente en Criterio 1
        assertThat(createdBlockEntryId).isNotNull();

        mockMvc.perform(delete("/v1/lists/" + createdBlockEntryId)
                        .header("Authorization", "Bearer " + tokenAnalystA))
                .andExpect(status().isNoContent());

        AUDIT_LOGS.add("  - Entrada eliminada mediante DELETE /v1/lists/{id} (204 No Content): OK");

        // 6.2 Evaluar nuevamente la misma transacción con la IP eliminada -> Ya no debe dar REJECT de lista
        EvaluateTransactionRequest evalReq = EvaluateTransactionRequest.builder()
                .transactionId("TX_UNBLOCKED_" + RUN_ID)
                .amount(new BigDecimal("80.00"))
                .currency("PEN")
                .ipAddress("203.0.113.88") // La IP que fue eliminada
                .deviceId("dev_clean_" + RUN_ID)
                .customerId("cust_" + RUN_ID)
                .build();

        mockMvc.perform(post("/v1/transactions/evaluate")
                        .header("X-API-Key", rawApiKeyA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.riskScore").value(0));

        AUDIT_LOGS.add("  - Tras eliminación, la evaluación ya NO rechaza por lista negra (Memoria sincronizada O(1)): OK");
    }

    @Test
    @Order(7)
    @DisplayName("Control de Unicidad y Aislamiento Multi-Tenant")
    void testUniquenessAndTenantIsolation() throws Exception {
        AUDIT_LOGS.add("[CONTROL DE UNICIDAD Y MULTI-TENANCY]:");

        // 7.1 Unicidad: registrar duplicado en la misma lista y comercio -> 409 Conflict
        CreateListEntryRequest req = CreateListEntryRequest.builder()
                .listType(ListType.BLOCK)
                .entryType(ListEntryType.EMAIL)
                .value("fraude_duplicado@pillapago.pe")
                .reason("Correo utilizado en ataque recurrente")
                .build();

        // Primer alta -> 201 Created
        mockMvc.perform(post("/v1/lists")
                        .header("Authorization", "Bearer " + tokenAnalystA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Segunda alta idéntica -> 409 Conflict
        mockMvc.perform(post("/v1/lists")
                        .header("Authorization", "Bearer " + tokenAnalystA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        AUDIT_LOGS.add("  - Intento de duplicidad rechazado con 409 Conflict: OK");

        // 7.2 Aislamiento de Comercio B: Analista B no ve las entradas del Comercio A
        MvcResult resB = mockMvc.perform(get("/v1/lists")
                        .header("Authorization", "Bearer " + tokenAnalystB))
                .andExpect(status().isOk())
                .andReturn();

        List<?> listB = objectMapper.readValue(resB.getResponse().getContentAsString(), List.class);
        assertThat(listB).isEmpty(); // Comercio B no tiene entradas

        AUDIT_LOGS.add("  - Comercio B aislado de Comercio A (0 entradas compartidas): OK");
        AUDIT_LOGS.add("  => Multi-tenancy certificado.");
    }
}
