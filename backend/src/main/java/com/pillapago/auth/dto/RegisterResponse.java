package com.pillapago.auth.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterResponse {

    private String message;
    private String maskedEmail;
    private UUID merchantId;
    private String status;
}
