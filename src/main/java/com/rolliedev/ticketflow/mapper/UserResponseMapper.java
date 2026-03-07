package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.UserResponse;
import com.rolliedev.ticketflow.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserResponseMapper implements Mapper<UserEntity, UserResponse> {

    @Override
    public UserResponse map(UserEntity object) {
        return new UserResponse(
                object.getId(),
                object.getFullName(),
                object.getEmail(),
                object.getRole(),
                object.getCreatedAt()
        );
    }
}
