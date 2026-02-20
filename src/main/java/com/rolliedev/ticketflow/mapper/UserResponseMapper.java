package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.UserResponse;
import com.rolliedev.ticketflow.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserResponseMapper implements Mapper<UserEntity, UserResponse> {

    @Override
    public UserResponse toDto(UserEntity entity) {
        return new UserResponse(
                entity.getId(),
                entity.getFullName(),
                entity.getEmail(),
                entity.getRole(),
                entity.getCreatedAt()
        );
    }
}
