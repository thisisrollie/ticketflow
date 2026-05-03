package com.rolliedev.ticketflow.security;

import com.rolliedev.ticketflow.entity.UserEntity;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
public class TicketFlowUserDetails implements UserDetails {

    private final Integer id;
    private final String fullName;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public TicketFlowUserDetails(UserEntity userEntity) {
        this(userEntity.getId(),
                userEntity.getFullName(),
                userEntity.getEmail(),
                userEntity.getPassword(),
                Collections.singleton(userEntity.getRole()));
    }

    public TicketFlowUserDetails(Integer id, String fullName, String email, String password, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
    }

    @Override
    public String getUsername() {
        return email;
    }

    public boolean hasAuthority(GrantedAuthority authority) {
        return authorities.contains(authority);
    }
}
