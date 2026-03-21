package com.rolliedev.ticketflow.integration.http.rest;

import com.rolliedev.ticketflow.dto.ActorCommand;
import com.rolliedev.ticketflow.dto.CreateCommentRequest;
import com.rolliedev.ticketflow.service.TicketService;
import com.rolliedev.ticketflow.testsupport.base.AbstractSpringBootIT;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@RequiredArgsConstructor
class CommentRestControllerIT extends AbstractSpringBootIT {

    private static final Long TICKET_ID = 1L;
    private static final Integer ADMIN_ID = 1;
    private static final Integer AGENT_ID = 2;
    private static final Integer CUSTOMER_ID = 3;
    private static final Long COMMENT_ID = 1L;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final TicketService ticketService;

    @Test
    void shouldFindAllByTicketId() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{ticketId}/comments", TICKET_ID))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$").value(hasSize(2))
                );
    }

    @Test
    void shouldCreateComment() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest(CUSTOMER_ID, "Test comment");

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isCreated(),
                        jsonPath("$.id").exists(),
                        jsonPath("$.author.id").value(CUSTOMER_ID),
                        jsonPath("$.body").value("Test comment")
                );
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
    }

    @Test
    void shouldReturnConflictWhenTryingToAddCommentOnClosedTicket() throws Exception {
        ticketService.resolve(TICKET_ID, ADMIN_ID);
        ticketService.closeByCustomer(TICKET_ID, CUSTOMER_ID);

        CreateCommentRequest request = new CreateCommentRequest(CUSTOMER_ID, "test comment");

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isConflict(),
                        jsonPath("$.message").value("Closed tickets cannot be modified")
                );
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
    }

    @Test
    void shouldReturnConflictWhenTryingToDeleteCommentOnClosedTicket() throws Exception {
        ticketService.resolve(TICKET_ID, ADMIN_ID);
        ticketService.closeByCustomer(TICKET_ID, CUSTOMER_ID);

        ActorCommand request = new ActorCommand(ADMIN_ID);

        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}", TICKET_ID, COMMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isConflict(),
                        jsonPath("$.message").value("Closed tickets cannot be modified")
                );
    }

    @Test
    void shouldReturnForbiddenWhenTryingToDeleteCommentByNonAdminNorAuthor() throws Exception {
        ActorCommand request = new ActorCommand(AGENT_ID);

        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}", TICKET_ID, COMMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isForbidden(),
                        jsonPath("$.message").value("Only admins or the comment author can delete a comment")
                );
    }
}