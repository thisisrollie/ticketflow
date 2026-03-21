package com.rolliedev.ticketflow.unit.http.rest;

import com.rolliedev.ticketflow.dto.ActorCommand;
import com.rolliedev.ticketflow.dto.CommentResponse;
import com.rolliedev.ticketflow.dto.CreateCommentRequest;
import com.rolliedev.ticketflow.dto.UserSummary;
import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.TicketFlowAccessDeniedException;
import com.rolliedev.ticketflow.http.handler.RestControllerExceptionHandler;
import com.rolliedev.ticketflow.http.rest.CommentRestController;
import com.rolliedev.ticketflow.service.CommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentRestController.class)
@Import(RestControllerExceptionHandler.class)
public class CommentRestControllerTest {

    private static final Long TICKET_ID = 1L;
    private static final Long COMMENT_ID = 1L;
    private static final Integer ADMIN_ID = 1;
    private static final Integer AGENT_ID = 2;
    private static final Integer CUSTOMER_ID = 3;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommentService commentService;

    @Test
    void shouldFindAllByTicketId() throws Exception {
        doReturn(List.of(commentResponse())).when(commentService).findAllBy(TICKET_ID);

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/comments", TICKET_ID))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$").value(hasSize(1)),
                        jsonPath("$[0].id").value(COMMENT_ID),
                        jsonPath("$[0].body").value("Test comment")
                );

        verify(commentService).findAllBy(TICKET_ID);
    }

    @Test
    void shouldCreateComment() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest(CUSTOMER_ID, "Test comment");

        doReturn(commentResponse()).when(commentService).create(TICKET_ID, CUSTOMER_ID, "Test comment");

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isCreated(),
                        jsonPath("$.id").exists(),
                        jsonPath("$.author.id").value(CUSTOMER_ID),
                        jsonPath("$.body").value("Test comment")
                );

        verify(commentService).create(TICKET_ID, CUSTOMER_ID, "Test comment");
    }

    @Test
    void shouldReturnBadRequestWhenCreateCommentIsInvalid() throws Exception {
        CreateCommentRequest invalidRequest = new CreateCommentRequest(null, "");

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpectAll(
                        status().isBadRequest()
                );

        verify(commentService, never()).create(any(), any(), any());
    }

    @Test
    void shouldReturnConflictWhenTryingToAddCommentOnClosedTicket() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest(CUSTOMER_ID, "test comment");

        doThrow(new BusinessRuleViolationException("Closed tickets cannot be modified"))
                .when(commentService).create(TICKET_ID, CUSTOMER_ID, "test comment");

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isConflict(),
                        jsonPath("$.message").value("Closed tickets cannot be modified")
                );

        verify(commentService).create(TICKET_ID, CUSTOMER_ID, "test comment");
    }

    @Test
    void shouldDeleteComment() throws Exception {
        ActorCommand request = new ActorCommand(ADMIN_ID);

        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}", TICKET_ID, COMMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isNoContent()
                );

        verify(commentService).delete(TICKET_ID, COMMENT_ID, ADMIN_ID);
    }

    @Test
    void shouldReturnBadRequestWhenDeleteRequestIsInvalid() throws Exception {
        ActorCommand invalidRequest = new ActorCommand(null);

        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}", TICKET_ID, COMMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpectAll(
                        status().isBadRequest()
                );

        verify(commentService, never()).delete(any(), any(), any());
    }

    @Test
    void shouldReturnForbiddenWhenTryingToDeleteCommentByNonAdminNorAuthor() throws Exception {
        ActorCommand request = new ActorCommand(AGENT_ID);

        doThrow(new TicketFlowAccessDeniedException("Only admins or the comment author can delete a comment"))
                .when(commentService).delete(TICKET_ID, COMMENT_ID, AGENT_ID);

        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}", TICKET_ID, COMMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isForbidden(),
                        jsonPath("$.message").value("Only admins or the comment author can delete a comment")
                );

        verify(commentService).delete(TICKET_ID, COMMENT_ID, AGENT_ID);
    }

    private CommentResponse commentResponse() {
        return new CommentResponse(
                COMMENT_ID,
                new UserSummary(CUSTOMER_ID, "Clark Kent"),
                "Test comment",
                Instant.parse("2026-03-17T12:30:00Z")
        );
    }
}
