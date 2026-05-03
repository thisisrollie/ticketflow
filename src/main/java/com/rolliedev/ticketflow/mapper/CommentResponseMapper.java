package com.rolliedev.ticketflow.mapper;

import com.rolliedev.ticketflow.dto.CommentResponse;
import com.rolliedev.ticketflow.entity.TicketCommentEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentResponseMapper implements Mapper<TicketCommentEntity, CommentResponse> {

    private final UserSummaryMapper userSummaryMapper;

    @Override
    public CommentResponse map(TicketCommentEntity object) {
        return new CommentResponse(
                object.getId(),
                userSummaryMapper.map(object.getAuthor()),
                object.getBody(),
                object.getCreatedAt()
        );
    }
}
