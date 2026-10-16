package com.pillapago.auth.dto;

import com.pillapago.tenant.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private UUID id;
    private UUID merchantId;
    private String email;
    private String username;
    private UserRole role;
}
