package com.rolliedev.ticketflow.entity.enums;

import org.springframework.security.core.GrantedAuthority;

public enum Role implements GrantedAuthority {
    CUSTOMER,
    AGENT,
    ADMIN;

    @Override
    public String getAuthority() {
        return name();
    }
}
