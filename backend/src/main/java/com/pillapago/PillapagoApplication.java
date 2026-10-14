package com.pillapago;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicación Pillapago.
 * La anotación @SpringBootApplication escanea todos los sub-paquetes
 * de com.pillapago automáticamente (auth, tenant, evaluation, rules, etc.)
 */
@SpringBootApplication
public class PillapagoApplication {

    public static void main(String[] args) {
        SpringApplication.run(PillapagoApplication.class, args);
    }
}
