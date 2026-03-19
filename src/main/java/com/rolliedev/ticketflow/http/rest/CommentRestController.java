package com.rolliedev.ticketflow.http.rest;

import com.rolliedev.ticketflow.dto.ActorCommand;
import com.rolliedev.ticketflow.dto.CommentResponse;
import com.rolliedev.ticketflow.dto.CreateCommentRequest;
import com.rolliedev.ticketflow.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/comments")
@RequiredArgsConstructor
public class CommentRestController {

    private final CommentService commentService;

    @GetMapping
    public List<CommentResponse> findAllBy(@PathVariable Long ticketId) {
        return commentService.findAllBy(ticketId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(@PathVariable Long ticketId,
                                  @Validated @RequestBody CreateCommentRequest comment) {
        return commentService.create(ticketId, comment.getAuthorId(), comment.getText());
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long ticketId,
                       @PathVariable Long commentId,
                       @Validated @RequestBody ActorCommand cmd) {
        commentService.delete(ticketId, commentId, cmd.getActorId());
    }
}
