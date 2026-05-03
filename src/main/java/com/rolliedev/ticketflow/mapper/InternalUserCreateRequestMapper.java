package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.InternalUserCreateRequest;
import com.rolliedev.ticketflow.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InternalUserCreateRequestMapper implements Mapper<InternalUserCreateRequest, UserEntity> {

    private final PasswordEncoder passwordEncoder;

    @Override
    public UserEntity map(InternalUserCreateRequest object) {
        return UserEntity.builder()
                .fullName(object.firstName() + " " + object.lastName())
                .email(object.email())
                .password(passwordEncoder.encode(object.rawPassword()))
                .role(object.role())
                .build();
    }
}
