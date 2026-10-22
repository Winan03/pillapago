package com.pillapago.auth.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

    private StrongPasswordValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StrongPasswordValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Password123!",
            "Admin_2026#Secure",
            "Pillapago$2026",
            "C0ntr@seniaFuerte!"
    })
    @DisplayName("Debe aceptar contraseñas que cumplan todos los requisitos de complejidad (RNF-10)")
    void shouldAcceptValidStrongPasswords(String validPassword) {
        assertThat(validator.isValid(validPassword, null)).isTrue();
    }

    @Test
    @DisplayName("Debe rechazar contraseñas de menos de 8 caracteres")
    void shouldRejectShortPasswords() {
        assertThat(validator.isValid("Pass1!", null)).isFalse();
        assertThat(validator.isValid("", null)).isFalse();
        assertThat(validator.isValid(null, null)).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar contraseñas sin mayúsculas")
    void shouldRejectWithoutUppercase() {
        assertThat(validator.isValid("password123!", null)).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar contraseñas sin minúsculas")
    void shouldRejectWithoutLowercase() {
        assertThat(validator.isValid("PASSWORD123!", null)).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar contraseñas sin números")
    void shouldRejectWithoutDigits() {
        assertThat(validator.isValid("Password!Special", null)).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar contraseñas sin caracteres especiales")
    void shouldRejectWithoutSpecialChars() {
        assertThat(validator.isValid("Password123456", null)).isFalse();
    }
}
