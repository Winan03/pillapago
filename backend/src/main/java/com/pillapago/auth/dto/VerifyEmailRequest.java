package com.pillapago.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyEmailRequest {

    @NotBlank(message = "El correo electrónico es obligatorio")
    private String email;

    @NotBlank(message = "El código de verificación es obligatorio")
    @Pattern(regexp = "^\\d{6}$", message = "El código debe contener exactamente 6 dígitos numéricos")
    private String code;
}
