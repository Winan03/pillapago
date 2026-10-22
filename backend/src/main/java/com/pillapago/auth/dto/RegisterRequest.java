package com.pillapago.auth.dto;

import com.pillapago.auth.validator.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "El nombre del comercio es obligatorio")
    @Size(max = 255, message = "El nombre del comercio no puede superar 255 caracteres")
    private String merchantName;

    @NotBlank(message = "El nombre del administrador es obligatorio")
    @Size(max = 255, message = "El nombre del administrador no puede superar 255 caracteres")
    private String adminName;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 255, message = "El correo electrónico no puede superar 255 caracteres")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @StrongPassword
    private String password;

    @NotBlank(message = "La confirmación de contraseña es obligatoria")
    private String confirmPassword;
}
