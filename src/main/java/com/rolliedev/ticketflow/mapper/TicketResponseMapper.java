package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.TicketResponse;
import com.rolliedev.ticketflow.entity.TicketEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketResponseMapper implements Mapper<TicketEntity, TicketResponse> {

    private final UserResponseMapper userResponseMapper;

    @Override
    public TicketResponse toDto(TicketEntity entity) {
        return new TicketResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getPriority(),
                userResponseMapper.toDto(entity.getCreatedBy()),
                entity.getAssignedTo() != null ? userResponseMapper.toDto(entity.getAssignedTo()) : null,
                entity.getCreatedAt(),
                entity.getModifiedAt(),
                entity.getResolvedAt()
        );
    }
}
