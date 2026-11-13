package com.payflow.identity.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class PayflowUserPrincipal implements UserDetails {

    private final String userId;
    private final String email;
    private final String role;
    private final String merchantId;

    public PayflowUserPrincipal(String userId, String email, String role, String merchantId) {
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.merchantId = merchantId;
    }

    public String getUserId() { return userId; }
    public String getRole() { return role; }
    public String getMerchantId() { return merchantId; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() { return null; }

    @Override
    public String getUsername() { return email; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}

