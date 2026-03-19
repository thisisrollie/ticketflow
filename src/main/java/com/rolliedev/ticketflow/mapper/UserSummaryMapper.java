package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.UserSummary;
import com.rolliedev.ticketflow.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserSummaryMapper implements Mapper<UserEntity, UserSummary> {

    @Override
    public UserSummary map(UserEntity object) {
        return new UserSummary(
                object.getId(),
                object.getFullName()
        );
    }
}
