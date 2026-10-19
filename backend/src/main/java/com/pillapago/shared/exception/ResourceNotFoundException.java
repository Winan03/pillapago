package com.pillapago.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción para recursos no encontrados o inaccesibles por aislamiento multi-tenant.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final String userMessage;

    public ResourceNotFoundException(String message) {
        super(message);
        this.userMessage = message;
    }

    public ResourceNotFoundException(String message, String userMessage) {
        super(message);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
