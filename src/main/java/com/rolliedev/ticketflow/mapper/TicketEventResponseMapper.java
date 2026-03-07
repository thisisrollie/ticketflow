package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.TicketEventResponse;
import com.rolliedev.ticketflow.dto.UserSummary;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import org.springframework.stereotype.Component;

@Component
public class TicketEventResponseMapper implements Mapper<TicketEventEntity, TicketEventResponse> {

    @Override
    public TicketEventResponse map(TicketEventEntity object) {
        return new TicketEventResponse(
                object.getId(),
                new UserSummary(object.getActor().getId(), object.getActor().getFullName()),
                object.getEventType(),
                object.getPayload(),
                object.getCreatedAt()
        );
    }
}
