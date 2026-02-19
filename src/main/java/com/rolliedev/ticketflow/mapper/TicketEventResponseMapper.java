package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.TicketEventResponse;
import com.rolliedev.ticketflow.dto.UserSummary;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import org.springframework.stereotype.Component;

@Component
public class TicketEventResponseMapper implements Mapper<TicketEventEntity, TicketEventResponse> {

    @Override
    public TicketEventResponse toDto(TicketEventEntity entity) {
        return new TicketEventResponse(
                entity.getId(),
                new UserSummary(entity.getActor().getId(), entity.getActor().getFullName()),
                entity.getEventType(),
                entity.getPayload(),
                entity.getCreatedAt()
        );
    }
}
