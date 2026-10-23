package com.pillapago.auth.dto;

import com.pillapago.auth.validator.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Petición para ejecutar el restablecimiento de contraseña utilizando un token de un solo uso (HU-03).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "El token de recuperación es obligatorio")
    private String token;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @StrongPassword
    private String password;

    @NotBlank(message = "La confirmación de la contraseña es obligatoria")
    private String confirmPassword;
}
