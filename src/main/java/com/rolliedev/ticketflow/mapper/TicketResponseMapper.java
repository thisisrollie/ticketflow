package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.TicketResponse;
import com.rolliedev.ticketflow.entity.TicketEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketResponseMapper implements Mapper<TicketEntity, TicketResponse> {

    private final UserSummaryMapper userSummaryMapper;

    @Override
    public TicketResponse map(TicketEntity object) {
        return new TicketResponse(
                object.getId(),
                object.getTitle(),
                object.getDescription(),
                object.getStatus(),
                object.getPriority(),
                userSummaryMapper.map(object.getCreatedBy()),
                object.getAssignedTo() != null ? userSummaryMapper.map(object.getAssignedTo()) : null,
                object.getCreatedAt(),
                object.getModifiedAt(),
                object.getResolvedAt(),
                object.getFirstRespondedAt(),
                object.getFirstResponseDeadline(),
                object.getResolutionDeadline(),
                object.getResponseSlaStatus(),
                object.getResolutionSlaStatus()
        );
    }
}
