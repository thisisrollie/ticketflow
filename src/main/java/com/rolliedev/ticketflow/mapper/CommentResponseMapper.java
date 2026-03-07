package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.CommentResponse;
import com.rolliedev.ticketflow.dto.UserSummary;
import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import org.springframework.stereotype.Component;

@Component
public class CommentResponseMapper implements Mapper<TicketCommentEntity, CommentResponse> {

    @Override
    public CommentResponse map(TicketCommentEntity object) {
        return new CommentResponse(
                object.getId(),
                new UserSummary(object.getAuthor().getId(), object.getAuthor().getFullName()),
                object.getBody(),
                object.getCreatedAt()
        );
    }
}
