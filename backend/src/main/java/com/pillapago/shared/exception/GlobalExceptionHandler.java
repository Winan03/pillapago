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
 * Manejador global de excepciones bajo el estÃ¡ndar RFC 7807 (Problem Details).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({InvalidCredentialsException.class, BadCredentialsException.class})
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(Exception ex, HttpServletRequest request) {
        String userMsg = (ex.getMessage() != null && !ex.getMessage().isBlank() && !ex.getMessage().equals("Credenciales invÃ¡lidas"))
                ? ex.getMessage()
                : "El correo electrÃ³nico o la contraseÃ±a ingresados no coinciden. Por favor verifica tus datos e intenta nuevamente.";

        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/unauthorized",
                "title", "No autorizado",
                "status", HttpStatus.UNAUTHORIZED.value(),
                "detail", "Credenciales invÃ¡lidas",
                "userMessage", userMsg,
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
                "detail", "No cuenta con los permisos necesarios para realizar esta acciÃ³n",
                "userMessage", "No tienes permisos de Administrador para realizar esta acciÃ³n en tu comercio.",
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
                "title", "Datos invÃ¡lidos",
                "status", HttpStatus.BAD_REQUEST.value(),
                "detail", "La solicitud contiene campos invÃ¡lidos",
                "userMessage", "Revisa los datos del formulario antes de continuar.",
                "instance", request.getRequestURI(),
                "invalidParams", errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<Map<String, Object>> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex, HttpServletRequest request) {
        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/conflict",
                "title", "Conflicto de Registro",
                "status", HttpStatus.CONFLICT.value(),
                "detail", ex.getMessage(),
                "userMessage", "Este correo electrÃ³nico ya estÃ¡ registrado. Si ya tienes un comercio, inicia sesiÃ³n o recupera el acceso.",
                "instance", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountLocked(AccountLockedException ex, HttpServletRequest request) {
        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/account-locked",
                "title", "Cuenta Bloqueada Temporalmente",
                "status", HttpStatus.LOCKED.value(),
                "detail", ex.getMessage(),
                "userMessage", ex.getMessage(),
                "instance", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.LOCKED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(InvalidVerificationCodeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidVerificationCode(InvalidVerificationCodeException ex, HttpServletRequest request) {
        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/invalid-verification-code",
                "title", "CÃ³digo de VerificaciÃ³n InvÃ¡lido",
                "status", HttpStatus.BAD_REQUEST.value(),
                "detail", ex.getMessage(),
                "userMessage", ex.getMessage(),
                "instance", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<Map<String, Object>> handlePasswordMismatch(PasswordMismatchException ex, HttpServletRequest request) {
        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/validation",
                "title", "Datos invÃ¡lidos",
                "status", HttpStatus.BAD_REQUEST.value(),
                "detail", ex.getMessage(),
                "userMessage", "La contraseÃ±a y la confirmaciÃ³n no coinciden. AsegÃºrate de escribir exactamente la misma contraseÃ±a en ambos campos.",
                "instance", request.getRequestURI(),
                "invalidParams", Map.of("confirmPassword", ex.getMessage())
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
                "title", "CondiciÃ³n de regla invÃ¡lida",
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
    @ExceptionHandler({
            org.springframework.dao.OptimisticLockingFailureException.class,
            org.hibernate.StaleObjectStateException.class,
            jakarta.persistence.OptimisticLockException.class
    })
    public ResponseEntity<Map<String, Object>> handleOptimisticLockingConflict(Exception ex, HttpServletRequest request) {
        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/conflict",
                "title", "Conflicto de Concurrencia Optimista",
                "status", HttpStatus.CONFLICT.value(),
                "detail", "El registro fue modificado simultáneamente por otra transacción o usuario.",
                "userMessage", "El caso fue tomado o resuelto concurrentemente por otro analista. Por favor refresca la cola.",
                "instance", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(com.pillapago.evaluation.exception.CaseAlreadyResolvedException.class)
    public ResponseEntity<Map<String, Object>> handleCaseAlreadyResolved(com.pillapago.evaluation.exception.CaseAlreadyResolvedException ex, HttpServletRequest request) {
        Map<String, Object> problem = Map.of(
                "type", "https://pillapago.dev/errors/case-already-resolved",
                "title", "Caso Previamente Resuelto",
                "status", HttpStatus.CONFLICT.value(),
                "detail", ex.getMessage(),
                "userMessage", "Este caso ya ha sido resuelto y es inmutable. No se admiten modificaciones posteriores.",
                "instance", request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
