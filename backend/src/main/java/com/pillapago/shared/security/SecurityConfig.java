package com.pillapago.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

/**
 * Configuración central de seguridad Spring Security 6.x (Spring Boot 3.x).
 * Define política stateless, hashing BCrypt (costo 12) y autorización RBAC vía @PreAuthorize.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final ObjectMapper objectMapper;

    /**
     * PasswordEncoder con costo de trabajo (strength) = 12 obligatorio (RNF-10).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Rutas públicas de autenticación, verificación y recuperación (HU-01, HU-02, HU-03)
                        .requestMatchers(HttpMethod.POST,
                                "/v1/auth/login",
                                "/v1/auth/register",
                                "/v1/auth/verify-email",
                                "/v1/auth/resend-verification",
                                "/v1/auth/forgot-password",
                                "/v1/auth/reset-password",
                                "/v1/auth/refresh"
                        ).permitAll()
                        // Rutas públicas legales y de auditoría (Términos y Condiciones, Políticas de Privacidad)
                        .requestMatchers(HttpMethod.GET, "/v1/legal/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        // Cualquier otra ruta requiere autenticación explícita
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

            Map<String, Object> problem = Map.of(
                    "type", "https://pillapago.dev/errors/unauthorized",
                    "title", "No autorizado",
                    "status", 401,
                    "detail", "Acceso no autenticado o token JWT no proporcionado",
                    "userMessage", "Debes iniciar sesión con tus credenciales para acceder a este recurso.",
                    "instance", request.getRequestURI()
            );

            objectMapper.writeValue(response.getOutputStream(), problem);
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

            Map<String, Object> problem = Map.of(
                    "type", "https://pillapago.dev/errors/forbidden",
                    "title", "Acceso prohibido",
                    "status", 403,
                    "detail", "No cuenta con los privilegios suficientes para este recurso",
                    "userMessage", "No tienes permisos de Administrador para realizar esta acción en tu comercio.",
                    "instance", request.getRequestURI()
            );

            objectMapper.writeValue(response.getOutputStream(), problem);
        };
    }
}
