package com.pillapago.shared.security;

import com.pillapago.tenant.domain.User;
import com.pillapago.tenant.domain.UserRole;
import com.pillapago.tenant.domain.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Adaptador de usuario para Spring Security.
 * Encapsula la identidad y los roles del usuario autenticado en la sesión stateless.
 */
@Getter
@Builder
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final UUID merchantId;
    private final String email;
    private final String username;
    private final String password;
    private final UserRole role;
    private final boolean active;

    public static UserPrincipal fromUser(User user) {
        return UserPrincipal.builder()
                .id(user.getId())
                .merchantId(user.getMerchant() != null ? user.getMerchant().getId() : null)
                .email(user.getEmail())
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .role(user.getRole())
                .active(user.getStatus() == UserStatus.ACTIVE)
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email != null ? email : username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
