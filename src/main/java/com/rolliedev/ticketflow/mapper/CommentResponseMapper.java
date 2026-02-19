package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.CommentResponse;
import com.rolliedev.ticketflow.dto.UserSummary;
import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import org.springframework.stereotype.Component;

@Component
public class CommentResponseMapper implements Mapper<TicketCommentEntity, CommentResponse> {

    @Override
    public CommentResponse toDto(TicketCommentEntity entity) {
        return new CommentResponse(
                entity.getId(),
                new UserSummary(entity.getAuthor().getId(), entity.getAuthor().getFullName()),
                entity.getBody(),
                entity.getCreatedAt()
        );
    }
}
