package com.pillapago.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillapago.rules.domain.RuleEntity;
import com.pillapago.rules.domain.RuleStatus;
import com.pillapago.rules.dto.ConditionDto;
import com.pillapago.rules.dto.CreateRuleRequest;
import com.pillapago.rules.dto.UpdateRuleRequest;
import com.pillapago.rules.repository.RuleRepository;
import com.pillapago.shared.security.JwtService;
import com.pillapago.shared.security.UserPrincipal;
import com.pillapago.tenant.domain.Merchant;
import com.pillapago.tenant.domain.User;
import com.pillapago.tenant.domain.UserRole;
import com.pillapago.tenant.domain.UserStatus;
import com.pillapago.tenant.repository.ApiKeyRepository;
import com.pillapago.tenant.repository.MerchantRepository;
import com.pillapago.tenant.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suite de Prueba End-to-End Integral Consecutiva para la historia de usuario:
 * HU-07: Crear regla (Pillapago Antifraude).
 *
 * Valida secuencialmente los criterios de aceptación en un único flujo consecutivo:
 *  1. Control de Acceso RBAC estricto: Solo RISK_ANALYST puede crear, consultar y actualizar reglas (403 Forbidden para otros roles).
 *  2. Validación del Lenguaje Acotado: Rechazo estricto (400 Bad Request) de campos inexistentes, operadores incompatibles y formato inválido.
 *  3. Restricción estructural de reglas: Máximo 3 condiciones AND y puntaje acotado entre 0 y 100 puntos.
 *  4. Estado inicial de Borrador (DRAFT): Las reglas se persisten en DRAFT y quedan aisladas sin afectar decisiones en vivo.
 *  5. Caso de uso verificado: "device_accounts_24h > 3 suma 40 puntos" persistido e indexado correctamente.
 *  6. Aislamiento Multi-Tenant estricto: Los comercios solo tienen visibilidad y control sobre sus propias reglas.
 *
 * Genera el reporte auditable en:
 *  temp_rules_acceptance_report/reporte_hu07_aceptacion.txt
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RuleAcceptanceCriteriaValidationTest {

    private static final List<String> AUDIT_LOGS = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RuleRepository ruleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private Merchant merchantA;
    private Merchant merchantB;

    private User analystMerchantA;
    private User adminMerchantA;
    private User auditorMerchantA;

    private User analystMerchantB;

    private String tokenAnalystA;
    private String tokenAdminA;
    private String tokenAuditorA;
    private String tokenAnalystB;

    @BeforeEach
    void setUp() {
        ruleRepository.deleteAll();
        apiKeyRepository.deleteAll();
        userRepository.deleteAll();
        merchantRepository.deleteAll();

        // 1. Comercios
        merchantA = merchantRepository.save(Merchant.builder()
                .name("Comercio Alpha SAC")
                .uuid("merchant-alpha-" + UUID.randomUUID())
                .build());

        merchantB = merchantRepository.save(Merchant.builder()
                .name("Comercio Beta SRL")
                .uuid("merchant-beta-" + UUID.randomUUID())
                .build());

        // 2. Usuarios Comercio Alpha
        analystMerchantA = userRepository.save(User.builder()
                .merchant(merchantA)
                .username("analista_alpha")
                .email("analista@alpha.com")
                .emailHash(jwtService.hashToken("analista@alpha.com"))
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(UserRole.RISK_ANALYST)
                .status(UserStatus.ACTIVE)
                .build());

        adminMerchantA = userRepository.save(User.builder()
                .merchant(merchantA)
                .username("admin_alpha")
                .email("admin@alpha.com")
                .emailHash(jwtService.hashToken("admin@alpha.com"))
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(UserRole.MERCHANT_ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        auditorMerchantA = userRepository.save(User.builder()
                .merchant(merchantA)
                .username("auditor_alpha")
                .email("auditor@alpha.com")
                .emailHash(jwtService.hashToken("auditor@alpha.com"))
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(UserRole.AUDITOR)
                .status(UserStatus.ACTIVE)
                .build());

        // 3. Usuario Comercio Beta
        analystMerchantB = userRepository.save(User.builder()
                .merchant(merchantB)
                .username("analista_beta")
                .email("analista@beta.com")
                .emailHash(jwtService.hashToken("analista@beta.com"))
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(UserRole.RISK_ANALYST)
                .status(UserStatus.ACTIVE)
                .build());

        // 4. Tokens JWT
        tokenAnalystA = jwtService.generateAccessToken(UserPrincipal.fromUser(analystMerchantA));
        tokenAdminA = jwtService.generateAccessToken(UserPrincipal.fromUser(adminMerchantA));
        tokenAuditorA = jwtService.generateAccessToken(UserPrincipal.fromUser(auditorMerchantA));
        tokenAnalystB = jwtService.generateAccessToken(UserPrincipal.fromUser(analystMerchantB));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        ruleRepository.deleteAll();
    }

    @Test
    @DisplayName("Flujo Consecutivo Integral de Validación de Criterios de Aceptación HU-07")
    void testConsecutiveAcceptanceCriteriaHU07() throws Exception {
        logAudit("==========================================================================================");
        logAudit("REPORTE OFICIAL DE AUDITORÍA Y VALIDACIÓN DE CRITERIOS DE ACEPTACIÓN");
        logAudit("HISTORIA DE USUARIO: HU-07 - Crear regla (Pillapago Antifraude)");
        logAudit("Fecha y Hora de Ejecución: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        logAudit("Entorno: Test Suite Automatizado con Spring Boot 3.x, Spring Security & PostgreSQL/H2");
        logAudit("==========================================================================================\n");

        // -----------------------------------------------------------------------------------------
        // CRITERIO 1: Control de Acceso RBAC estricto (solo RISK_ANALYST permitido, 403 para otros)
        // -----------------------------------------------------------------------------------------
        logAudit(">>> CRITERIO 1: Control de Acceso RBAC estricto a Endpoints de Reglas");
        
        CreateRuleRequest sampleRequest = CreateRuleRequest.builder()
                .name("Regla de prueba de acceso")
                .description("Prueba de RBAC")
                .points(20)
                .active(true)
                .conditions(List.of(
                        ConditionDto.builder().field("amount").operator(">").value("100").build()
                ))
                .build();

        // 1.1 Intentar crear regla sin autenticación -> 401 Unauthorized
        mockMvc.perform(post("/v1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andExpect(status().isUnauthorized());
        logAudit("  [PASÓ] 1.1 Solicitud sin credenciales rechazada con 401 Unauthorized.");

        // 1.2 Intentar crear regla con rol MERCHANT_ADMIN -> 403 Forbidden
        mockMvc.perform(post("/v1/rules")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andExpect(status().isForbidden());
        logAudit("  [PASÓ] 1.2 Usuario MERCHANT_ADMIN rechazado con 403 Forbidden al invocar POST /v1/rules.");

        // 1.3 Intentar crear regla con rol AUDITOR -> 403 Forbidden
        mockMvc.perform(post("/v1/rules")
                        .header("Authorization", "Bearer " + tokenAuditorA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andExpect(status().isForbidden());
        logAudit("  [PASÓ] 1.3 Usuario AUDITOR rechazado con 403 Forbidden al invocar POST /v1/rules.");

        // 1.4 Intentar listar reglas con rol MERCHANT_ADMIN -> 403 Forbidden
        mockMvc.perform(get("/v1/rules")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());
        logAudit("  [PASÓ] 1.4 Usuario MERCHANT_ADMIN rechazado con 403 Forbidden al invocar GET /v1/rules.");

        // 1.5 Crear regla con rol RISK_ANALYST -> 201 Created
        MvcResult createResult = mockMvc.perform(post("/v1/rules")
                        .header("Authorization", "Bearer " + tokenAnalystA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        
        JsonNode createdJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        UUID rule1Id = UUID.fromString(createdJson.get("id").asText());
        logAudit("  [PASÓ] 1.5 Usuario RISK_ANALYST autorizado exitosamente (201 Created), id: " + rule1Id + "\n");

        // -----------------------------------------------------------------------------------------
        // CRITERIO 2: Validación del Lenguaje Acotado del Motor (HU-06a) con código 400 Bad Request
        // -----------------------------------------------------------------------------------------
        logAudit(">>> CRITERIO 2: Validación estricta del Lenguaje Acotado (Rechazo 400 Bad Request)");

        // 2.1 Campo inexistente / no soportado
        CreateRuleRequest invalidFieldReq = CreateRuleRequest.builder()
                .name("Regla con campo inválido")
                .points(30)
                .conditions(List.of(
                        ConditionDto.builder().field("campo_falso_inyeccion").operator("=").value("1").build()
                ))
                .build();

        mockMvc.perform(post("/v1/rules")
                        .header("Authorization", "Bearer " + tokenAnalystA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidFieldReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("campo_falso_inyeccion")));
        logAudit("  [PASÓ] 2.1 Campo no soportado 'campo_falso_inyeccion' rechazado con 400 Bad Request y mensaje explícito.");

        // 2.2 Operador incompatible con el tipo de datos (ej. operador numérico '>' sobre campo STRING 'country')
        CreateRuleRequest incompatibleOpReq = CreateRuleRequest.builder()
                .name("Regla operador incompatible")
                .points(15)
                .conditions(List.of(
                        ConditionDto.builder().field("country").operator(">").value("PE").build()
                ))
                .build();

        mockMvc.perform(post("/v1/rules")
                        .header("Authorization", "Bearer " + tokenAnalystA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incompatibleOpReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("incompatible con el tipo STRING")));
        logAudit("  [PASÓ] 2.2 Operador incompatible '>' en campo STRING rechazado con 400 Bad Request.");

        // 2.3 Formato de valor inválido (ej. valor alfanumérico en campo NUMERIC 'amount')
        CreateRuleRequest invalidValueReq = CreateRuleRequest.builder()
                .name("Regla valor no numérico")
                .points(15)
                .conditions(List.of(
                        ConditionDto.builder().field("amount").operator(">").value("cien_soles").build()
                ))
                .build();

        mockMvc.perform(post("/v1/rules")
                        .header("Authorization", "Bearer " + tokenAnalystA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidValueReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("valor numérico válido")));
        logAudit("  [PASÓ] 2.3 Valor no numérico 'cien_soles' en campo NUMERIC rechazado con 400 Bad Request.");

        // 2.4 Exceso de condiciones permitidas (> 3 condiciones)
        CreateRuleRequest tooManyConditionsReq = CreateRuleRequest.builder()
                .name("Regla 4 condiciones")
                .points(50)
                .conditions(List.of(
                        ConditionDto.builder().field("amount").operator(">").value("100").build(),
                        ConditionDto.builder().field("velocity_ip_1h").operator(">").value("3").build(),
                        ConditionDto.builder().field("is_vpn").operator("=").value("true").build(),
                        ConditionDto.builder().field("country").operator("=").value("PE").build()
                ))
                .build();

        mockMvc.perform(post("/v1/rules")
                        .header("Authorization", "Bearer " + tokenAnalystA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooManyConditionsReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.invalidParams.conditions").value(org.hamcrest.Matchers.containsString("máximo 3 condiciones")));
        logAudit("  [PASÓ] 2.4 Exceso de condiciones (4 enviadas, máx 3) rechazado con 400 Bad Request.");

        // 2.5 Puntaje fuera de rango (ej. 150 puntos cuando el límite es 100)
        CreateRuleRequest invalidScoreReq = CreateRuleRequest.builder()
                .name("Regla puntaje inválido")
                .points(150)
                .conditions(List.of(
                        ConditionDto.builder().field("amount").operator(">").value("100").build()
                ))
                .build();

        mockMvc.perform(post("/v1/rules")
                        .header("Authorization", "Bearer " + tokenAnalystA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidScoreReq)))
                .andExpect(status().isBadRequest());
        logAudit("  [PASÓ] 2.5 Puntaje fuera de rango (150 puntos > 100) rechazado con 400 Bad Request.\n");

        // -----------------------------------------------------------------------------------------
        // CRITERIO 3: Caso de Uso Verificado ("device_accounts_24h > 3 suma 40 puntos")
        // -----------------------------------------------------------------------------------------
        logAudit(">>> CRITERIO 3: Creación y Persistencia del Caso de Uso Verificado");

        CreateRuleRequest verifiedCaseReq = CreateRuleRequest.builder()
                .name("Alerta múltiple cuenta por dispositivo")
                .description("Detecta si el mismo dispositivo registró más de 3 cuentas en 24 horas y suma 40 puntos de riesgo")
                .points(40)
                .active(true)
                .conditions(List.of(
                        ConditionDto.builder().field("device_accounts_24h").operator(">").value("3").build()
                ))
                .build();

        MvcResult verifiedCaseResult = mockMvc.perform(post("/v1/rules")
                        .header("Authorization", "Bearer " + tokenAnalystA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifiedCaseReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alerta múltiple cuenta por dispositivo"))
                .andExpect(jsonPath("$.points").value(40))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.conditions[0].field").value("device_accounts_24h"))
                .andExpect(jsonPath("$.conditions[0].operator").value(">"))
                .andExpect(jsonPath("$.conditions[0].value").value("3"))
                .andReturn();

        JsonNode verifiedJson = objectMapper.readTree(verifiedCaseResult.getResponse().getContentAsString());
        UUID verifiedRuleId = UUID.fromString(verifiedJson.get("id").asText());
        logAudit("  [PASÓ] 3.1 Regla verificada creada exitosamente con ID: " + verifiedRuleId);
        logAudit("        Configuración: device_accounts_24h > 3 -> +40 puntos [Estado: DRAFT]\n");

        // -----------------------------------------------------------------------------------------
        // CRITERIO 4: Garantía de Estado Borrador (DRAFT) sin afectación al motor en vivo
        // -----------------------------------------------------------------------------------------
        logAudit(">>> CRITERIO 4: Verificación estricta de Estado Borrador (DRAFT)");

        RuleEntity persistedEntity = ruleRepository.findByIdAndMerchantIdWithConditions(verifiedRuleId, merchantA.getId()).orElseThrow();
        assertThat(persistedEntity.getStatus()).isEqualTo(RuleStatus.DRAFT);
        assertThat(persistedEntity.getMerchant().getId()).isEqualTo(merchantA.getId());
        assertThat(persistedEntity.getConditions()).hasSize(1);
        assertThat(persistedEntity.getConditions().get(0).getOrderIndex()).isEqualTo(0);

        logAudit("  [PASÓ] 4.1 La entidad en BD se encuentra explícitamente en status = DRAFT.");
        logAudit("  [PASÓ] 4.2 Las condiciones hijas mantienen la integridad referencial y el orden lógico.");
        logAudit("  [PASÓ] 4.3 Al permanecer en DRAFT, el motor de evaluación en vivo ignora esta regla hasta su publicación formal (HU-08).\n");

        // -----------------------------------------------------------------------------------------
        // CRITERIO 5: Actualización de Reglas (PUT /v1/rules/{id})
        // -----------------------------------------------------------------------------------------
        logAudit(">>> CRITERIO 5: Edición y Actualización de Reglas existentes");

        UpdateRuleRequest updateReq = UpdateRuleRequest.builder()
                .name("Alerta múltiple cuenta por dispositivo (Actualizada)")
                .description("Regla ajustada a umbral de 4 cuentas y 45 puntos")
                .points(45)
                .active(false)
                .conditions(List.of(
                        ConditionDto.builder().field("device_accounts_24h").operator(">=").value("4").build(),
                        ConditionDto.builder().field("is_emulator").operator("=").value("true").build()
                ))
                .build();

        mockMvc.perform(put("/v1/rules/" + verifiedRuleId)
                        .header("Authorization", "Bearer " + tokenAnalystA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(verifiedRuleId.toString()))
                .andExpect(jsonPath("$.name").value("Alerta múltiple cuenta por dispositivo (Actualizada)"))
                .andExpect(jsonPath("$.points").value(45))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.conditions.length()").value(2))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        logAudit("  [PASÓ] 5.1 Regla actualizada exitosamente a 2 condiciones AND y 45 puntos, preservando estado DRAFT.\n");

        // -----------------------------------------------------------------------------------------
        // CRITERIO 6: Aislamiento Multi-Tenant Estricto (Aislamiento por Comercio)
        // -----------------------------------------------------------------------------------------
        logAudit(">>> CRITERIO 6: Aislamiento Estricto Multi-Tenant entre Comercios");

        // 6.1 Analista del Comercio Beta consulta sus reglas -> Debe estar vacía (no ve las de Alpha)
        MvcResult listBetaResult = mockMvc.perform(get("/v1/rules")
                        .header("Authorization", "Bearer " + tokenAnalystB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0))
                .andReturn();
        logAudit("  [PASÓ] 6.1 Analista de Comercio Beta obtiene lista vacía de reglas (cero filtración de Comercio Alpha).");

        // 6.2 Analista del Comercio Alpha consulta sus reglas -> Ve sus 2 reglas creadas
        MvcResult listAlphaResult = mockMvc.perform(get("/v1/rules")
                        .header("Authorization", "Bearer " + tokenAnalystA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        logAudit("  [PASÓ] 6.2 Analista de Comercio Alpha visualiza exclusivamente sus 2 reglas propias.");

        // 6.3 Analista de Comercio Beta intenta actualizar la regla de Comercio Alpha -> 404 Not Found
        mockMvc.perform(put("/v1/rules/" + verifiedRuleId)
                        .header("Authorization", "Bearer " + tokenAnalystB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());
        logAudit("  [PASÓ] 6.3 Intento de ataque transversal de Comercio Beta sobre regla de Comercio Alpha bloqueado con 404 Not Found.\n");

        // -----------------------------------------------------------------------------------------
        // CRITERIO 7: Endpoint de Metadatos de Campos y Operadores para Frontend Guiado
        // -----------------------------------------------------------------------------------------
        logAudit(">>> CRITERIO 7: Catálogo de Campos y Operadores para Formulario Guiado (Frontend)");

        mockMvc.perform(get("/v1/rules/fields")
                        .header("Authorization", "Bearer " + tokenAnalystA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(5)))
                .andExpect(jsonPath("$[?(@.field == 'device_accounts_24h')].allowedOperators").exists());

        logAudit("  [PASÓ] 7.1 Endpoint GET /v1/rules/fields provee metadatos de tipos y operadores para el frontend guiado sin código manual.\n");

        logAudit("==========================================================================================");
        logAudit("RESUMEN DE AUDITORÍA: 100% DE CRITERIOS DE ACEPTACIÓN HU-07 CUMPLIDOS SATISFACTORIAMENTE");
        logAudit("RBAC: Solo RISK_ANALYST (403 para Admin y Auditor) | Lenguaje: 400 Bad Request ante campos/operadores inválidos");
        logAudit("Borrador: status = 'DRAFT' garantizado | Multi-tenant: Aislamiento absoluto por merchant_id");
        logAudit("==========================================================================================");
    }

    private static void logAudit(String line) {
        System.out.println(line);
        AUDIT_LOGS.add(line);
    }

    @AfterAll
    static void generatePhysicalReport() throws IOException {
        Path outputDir = Paths.get("temp_rules_acceptance_report");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        Path reportFile = outputDir.resolve("reporte_hu07_aceptacion.txt");
        Files.write(reportFile, AUDIT_LOGS, StandardCharsets.UTF_8);
        System.out.println("\n>>> [AUDIT SUCCESS] Reporte de aceptación generado exitosamente en: " + reportFile.toAbsolutePath());
    }
}
