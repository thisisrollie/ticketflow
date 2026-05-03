package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.TicketEventResponse;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketEventResponseMapper implements Mapper<TicketEventEntity, TicketEventResponse> {

    private final UserSummaryMapper userSummaryMapper;

    @Override
    public TicketEventResponse map(TicketEventEntity object) {
        return new TicketEventResponse(
                object.getId(),
                userSummaryMapper.map(object.getActor()),
                object.getEventType(),
                object.getPayload(),
                object.getCreatedAt()
        );
    }
}
