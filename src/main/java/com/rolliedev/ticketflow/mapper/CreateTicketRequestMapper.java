package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.CreateTicketRequest;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CreateTicketRequestMapper implements Mapper<CreateTicketRequest, TicketEntity> {

    private final UserRepository userRepository;

    @Override
    public TicketEntity map(CreateTicketRequest object) {
        return TicketEntity.builder()
                .title(object.title())
                .description(object.description())
                .status(TicketStatus.NEW)
                .priority(TicketPriority.MEDIUM)
                .createdBy(getUser(object.creatorId()))
                .build();
    }

    private UserEntity getUser(Integer userId) {
        return Optional.of(userId)
                .flatMap(userRepository::findById)
                .orElseThrow(() -> ResourceNotFoundException.user(userId));
    }
}
