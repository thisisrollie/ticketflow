package com.rolliedev.ticketflow.integration.http.rest;

import com.rolliedev.ticketflow.dto.CreateCommentRequest;
import com.rolliedev.ticketflow.dto.PublicRegistrationRequest;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.testsupport.base.AbstractRestIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CommentRestControllerIT extends AbstractRestIT {

    private TicketEntity ticket1, ticket2;

    @BeforeEach
    void setUp() {
        ticket1 = ticketRepository.findById(1L).orElseThrow();
        ticket2 = ticketRepository.findById(2L).orElseThrow();
    }

    @Test
    void shouldReturnPagedCommentsWhenCalledByCustomerWhoOwnsTicket() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    void shouldReturnPagedCommentsWhenCalledByAgent() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .with(httpBasic("bruce.wayne@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.totalElements").value(3));
    }

    @Test
    void shouldReturnPagedCommentsWhenCalledByAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.totalElements").value(3));
    }

    @Test
    void shouldReturnEmptyPageWhenTicketHasNoComments() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{ticketId}/comments", ticket2.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void shouldReturnCorrectPageSizeWhenPageSizeParamIsProvided() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123"))
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.page").value(0))
                .andExpect(jsonPath("$.metadata.size").value(1))
                .andExpect(jsonPath("$.metadata.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void shouldReturn404WhenCustomerRequestsCommentsOnTicketTheyDoNotOwn() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new PublicRegistrationRequest("Lois", "Lane", "lois.lane@gmail.com", "pass123"))));

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .with(httpBasic("lois.lane@gmail.com", "pass123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404WhenFetchingCommentsForNonExistentTicket() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{ticketId}/comments", 999L)
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WhenFetchingCommentsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{ticketId}/comments", ticket1.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreateCommentWhenCalledByCustomerWhoOwnsTicket() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest("Still not working");

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Still not working"))
                .andExpect(jsonPath("$.author.email").value("clark.kent@gmail.com"));
    }

    @Test
    void shouldCreateCommentWhenCalledByAgent() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest("We are looking into this");

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .with(httpBasic("bruce.wayne@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("We are looking into this"))
                .andExpect(jsonPath("$.author.email").value("bruce.wayne@gmail.com"));
    }

    @Test
    void shouldTransitionTicketToInProgressWhenCustomerCommentsOnWaitingTicket() throws Exception {
        ticket1.setStatus(TicketStatus.WAITING_CUSTOMER);
        ticketRepository.save(ticket1);
        flushAndClear();

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCommentRequest("Here is the info you requested"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tickets/{id}", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(jsonPath("$.status").value(TicketStatus.IN_PROGRESS.name()));
    }

    @Test
    void shouldTransitionTicketToInProgressWhenCustomerCommentsOnResolvedTicket() throws Exception {
        ticket1.setStatus(TicketStatus.RESOLVED);
        ticketRepository.save(ticket1);
        flushAndClear();

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCommentRequest("Problem is still happening"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/tickets/{id}", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(jsonPath("$.status").value(TicketStatus.IN_PROGRESS.name()));
    }

    @Test
    void shouldReturn403WhenCustomerCommentsOnTicketTheyDoNotOwn() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new PublicRegistrationRequest("Lois", "Lane", "lois.lane@gmail.com", "pass123"))));

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .with(httpBasic("lois.lane@gmail.com", "pass123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCommentRequest("Can I help?"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn409WhenPostingCommentToClosedTicket() throws Exception {
        ticket1.setStatus(TicketStatus.CLOSED);
        ticketRepository.save(ticket1);
        flushAndClear();

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCommentRequest("Reopening this"))))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn400WhenCommentTextIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCommentRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenCommentBodyIsAbsentInRequest() throws Exception {
        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404WhenCreatingCommentForNonExistentTicket() throws Exception {
        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", 999L)
                        .with(httpBasic("clark.kent@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCommentRequest("Some comment"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WhenCreatingCommentWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateCommentRequest("Some comment"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldDeleteCommentWhenCalledByCommentAuthor() throws Exception {
        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}",
                        ticket1.getId(), 2L)
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/comments", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(jsonPath("$.metadata.totalElements").value(2));
    }

    @Test
    void shouldDeleteCommentWhenCalledByAdmin() throws Exception {
        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}",
                        ticket1.getId(), 1L)
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturn403WhenAgentTriesToDeleteAnotherUsersComment() throws Exception {
        // comment 2 belongs to clark.kent
        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}",
                        ticket1.getId(), 2L)
                        .with(httpBasic("bruce.wayne@gmail.com", "123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn409WhenDeletingCommentFromClosedTicket() throws Exception {
        ticket1.setStatus(TicketStatus.CLOSED);
        ticketRepository.save(ticket1);
        flushAndClear();

        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}",
                        ticket1.getId(), 2L)
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn400WhenCommentDoesNotBelongToSpecifiedTicket() throws Exception {
        // comment 1 belongs to ticket1, not ticket2
        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}",
                        ticket2.getId(), 1L)
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404WhenDeletingNonExistentComment() throws Exception {
        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}",
                        ticket1.getId(), 999L)
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WhenDeletingCommentWithoutAuthentication() throws Exception {
        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}",
                        ticket1.getId(), 1L))
                .andExpect(status().isUnauthorized());
    }
}
