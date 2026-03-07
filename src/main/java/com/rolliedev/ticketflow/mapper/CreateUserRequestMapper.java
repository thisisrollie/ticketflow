package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.CreateUserRequest;
import com.rolliedev.ticketflow.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class CreateUserRequestMapper implements Mapper<CreateUserRequest, UserEntity> {

    @Override
    public UserEntity map(CreateUserRequest object) {
        return UserEntity.builder()
                .fullName(object.firstName() + " " + object.lastName())
                .email(object.email())
                .role(object.role())
                .build();
    }
}
