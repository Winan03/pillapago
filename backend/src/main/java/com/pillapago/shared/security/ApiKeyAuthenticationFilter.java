package com.pillapago.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillapago.tenant.domain.ApiKey;
import com.pillapago.tenant.domain.UserRole;
import com.pillapago.tenant.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Filtro de autenticación para llamadas M2M / Tiendas que usan cabecera X-API-Key.
 * Valida la existencia y estado activo de la clave, rechazando de inmediato claves revocadas (HU-05).
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String apiKeyHeader = request.getHeader(API_KEY_HEADER);

        // Si no se envía la cabecera X-API-Key, permitimos que continúe el filtro JWT
        if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawKey = apiKeyHeader.trim();
        Optional<ApiKey> apiKeyOpt = apiKeyService.findByRawKey(rawKey);

        if (apiKeyOpt.isEmpty()) {
            sendUnauthorizedError(response, request.getRequestURI(),
                    "La clave de API proporcionada es incorrecta o no existe.",
                    "La clave de API provista no es válida. Verifique sus credenciales de integración en el panel de administración.");
            return;
        }

        ApiKey apiKey = apiKeyOpt.get();

        // Criterio HU-05: Si la clave fue revocada, deja de funcionar de inmediato
        if (Boolean.FALSE.equals(apiKey.getActive())) {
            sendUnauthorizedError(response, request.getRequestURI(),
                    "La clave de API ha sido revocada.",
                    "La API key utilizada ha sido revocada o es incorrecta. Genere una nueva clave activa desde su panel de administración.");
            return;
        }

        // Clave válida y activa: Construir autenticación para el SecurityContext
        UserPrincipal principal = UserPrincipal.builder()
                .id(apiKey.getId())
                .merchantId(apiKey.getMerchant().getId())
                .username("api-key:" + apiKey.getKeyPrefix())
                .role(UserRole.MERCHANT_ADMIN)
                .active(true)
                .build();

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_API_CLIENT"),
                        new SimpleGrantedAuthority("ROLE_" + UserRole.MERCHANT_ADMIN.name())
                )
        );

        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // Opcional: registrar timestamp de último uso
        apiKeyService.recordKeyUsage(apiKey);

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorizedError(HttpServletResponse response, String instance, String detail, String userMessage)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());

        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/unauthorized",
                "title", "Autenticación de API Key fallida",
                "status", HttpStatus.UNAUTHORIZED.value(),
                "detail", detail,
                "userMessage", userMessage,
                "instance", instance
        );

        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
