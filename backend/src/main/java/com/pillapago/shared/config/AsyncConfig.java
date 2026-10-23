package com.pillapago.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configuración de procesamiento asíncrono basado en Virtual Threads de Java 21 (HU-06c).
 * Garantiza que las tareas de persistencia y cifrado post-respuesta se ejecuten en hilos virtuales ligeros
 * sin bloquear la respuesta HTTP ni saturar el pool de conexiones.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "virtualThreadTaskExecutor")
    public Executor virtualThreadTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
