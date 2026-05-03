package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.PublicRegistrationRequest;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublicRegistrationRequestMapper implements Mapper<PublicRegistrationRequest, UserEntity> {

    private final PasswordEncoder passwordEncoder;

    @Override
    public UserEntity map(PublicRegistrationRequest object) {
        return UserEntity.builder()
                .fullName(object.firstName() + " " + object.lastName())
                .email(object.email())
                .password(passwordEncoder.encode(object.rawPassword()))
                .role(Role.CUSTOMER)
                .build();
    }
}
