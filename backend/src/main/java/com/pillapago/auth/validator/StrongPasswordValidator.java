package com.pillapago.auth.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    // Regex: al menos 8 caracteres, 1 mayúscula, 1 minúscula, 1 dígito, 1 carácter especial
    private static final Pattern HAS_UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern HAS_LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");
    private static final Pattern HAS_SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.length() < 8) {
            return false;
        }

        return HAS_UPPERCASE.matcher(password).find()
                && HAS_LOWERCASE.matcher(password).find()
                && HAS_DIGIT.matcher(password).find()
                && HAS_SPECIAL.matcher(password).find();
    }
}
