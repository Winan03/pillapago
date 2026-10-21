package com.pillapago.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Manejador global de excepciones bajo el estándar RFC 7807 (Problem Details).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({InvalidCredentialsException.class, BadCredentialsException.class})
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(Exception ex, HttpServletRequest request) {
        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/unauthorized",
                "title", "No autorizado",
                "status", HttpStatus.UNAUTHORIZED.value(),
                "detail", "Credenciales inválidas",
                "instance", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        Map<String, Object> problem = new HashMap<>();
        problem.put("type", "https://pillapago.dev/errors/rate-limit");
        problem.put("title", "Demasiadas peticiones");
        problem.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        problem.put("detail", ex.getMessage());
        problem.put("instance", request.getRequestURI());
        if (ex.getRetryAfterSeconds() > 0) {
            problem.put("retryAfterSeconds", ex.getRetryAfterSeconds());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (ex.getRetryAfterSeconds() > 0) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()));
        }

        return new ResponseEntity<>(problem, headers, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/not-found",
                "title", "Recurso no encontrado",
                "status", HttpStatus.NOT_FOUND.value(),
                "detail", ex.getMessage(),
                "userMessage", ex.getUserMessage() != null ? ex.getUserMessage() : ex.getMessage(),
                "instance", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/forbidden",
                "title", "Acceso prohibido",
                "status", HttpStatus.FORBIDDEN.value(),
                "detail", "No cuenta con los permisos necesarios para realizar esta acción",
                "userMessage", "No tienes permisos de Administrador para realizar esta acción en tu comercio.",
                "instance", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/validation",
                "title", "Datos inválidos",
                "status", HttpStatus.BAD_REQUEST.value(),
                "detail", "La solicitud contiene campos inválidos",
                "userMessage", "Revisa los datos del formulario antes de continuar.",
                "instance", request.getRequestURI(),
                "invalidParams", errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotencyConflict(IdempotencyConflictException ex, HttpServletRequest request) {
        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/idempotency-conflict",
                "title", "Conflicto de Idempotencia",
                "status", HttpStatus.CONFLICT.value(),
                "detail", ex.getMessage(),
                "userMessage", ex.getUserMessage() != null ? ex.getUserMessage() : "El identificador de transaccion ya fue utilizado con datos diferentes.",
                "instance", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(com.pillapago.rules.exception.InvalidRuleConditionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRuleCondition(com.pillapago.rules.exception.InvalidRuleConditionException ex, HttpServletRequest request) {
        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/invalid-rule-condition",
                "title", "Condición de regla inválida",
                "status", HttpStatus.BAD_REQUEST.value(),
                "detail", ex.getMessage(),
                "userMessage", ex.getMessage(),
                "instance", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(RedisUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleRedisUnavailable(RedisUnavailableException ex, HttpServletRequest request) {
        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/service-unavailable",
                "title", "Servicio temporalmente no disponible",
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "detail", ex.getMessage(),
                "userMessage", "El servicio de evaluacion en memoria esta experimentando interrupciones. Por favor reintente en breve.",
                "instance", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
