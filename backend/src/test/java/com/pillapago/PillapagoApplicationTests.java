package com.pillapago;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prueba de humo: verifica que el contexto de Spring arranca correctamente.
 * Usa el perfil "test" para conectarse a H2 en memoria (sin PostgreSQL real).
 */
@SpringBootTest
@ActiveProfiles("test")
class PillapagoApplicationTests {

    @Test
    void contextLoads() {
        // El contexto de Spring Boot arranca sin errores
    }
}
