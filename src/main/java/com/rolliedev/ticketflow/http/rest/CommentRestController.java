package com.rolliedev.ticketflow.http.rest;

import com.rolliedev.ticketflow.dto.CommentResponse;
import com.rolliedev.ticketflow.dto.CreateCommentRequest;
import com.rolliedev.ticketflow.dto.PageResponse;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.service.CommentService;
import com.rolliedev.ticketflow.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/comments")
@RequiredArgsConstructor
public class CommentRestController {

    private final TicketService ticketService;
    private final CommentService commentService;

    @GetMapping
    public PageResponse<CommentResponse> findAllBy(@PathVariable Long ticketId,
                                                   @AuthenticationPrincipal TicketFlowUserDetails currentUser,
                                                   @PageableDefault Pageable pageable) {
        ticketService.findById(ticketId, currentUser)
                .orElseThrow(() -> ResourceNotFoundException.ticket(ticketId));

        return PageResponse.of(commentService.findAllBy(ticketId, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(@PathVariable Long ticketId,
                                  @Validated @RequestBody CreateCommentRequest comment,
                                  @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        return commentService.create(ticketId, currentUser.getId(), comment.getText());
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long ticketId,
                       @PathVariable Long commentId,
                       @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        commentService.delete(ticketId, commentId, currentUser.getId());
    }
}
