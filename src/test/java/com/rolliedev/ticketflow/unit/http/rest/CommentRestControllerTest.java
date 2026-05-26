package com.rolliedev.ticketflow.unit.http.rest;

import com.rolliedev.ticketflow.config.SecurityConfiguration;
import com.rolliedev.ticketflow.dto.CommentResponse;
import com.rolliedev.ticketflow.dto.CreateCommentRequest;
import com.rolliedev.ticketflow.dto.TicketResponse;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.http.rest.CommentRestController;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.service.CommentService;
import com.rolliedev.ticketflow.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentRestController.class)
@Import(SecurityConfiguration.class)
public class CommentRestControllerTest {

    private static final Long TICKET_ID = 1L;
    private static final Integer ADMIN_ID = 1;
    private static final Long COMMENT_ID = 7L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommentService commentService;
    @MockitoBean
    private TicketService ticketService;

    private TicketFlowUserDetails adminDetails;

    @BeforeEach
    void setUp() {
        adminDetails = mockUserDetails(ADMIN_ID, Role.ADMIN);
    }

    @Test
    void shouldReturnPagedCommentsWhenTicketExist() throws Exception {
        List<CommentResponse> commentResponses = List.of(mockCommentResponse(1L), mockCommentResponse(2L));
        PageImpl<CommentResponse> page = new PageImpl<>(commentResponses, PageRequest.of(0, 10), 2);

        doReturn(Optional.of(mock(TicketResponse.class))).when(ticketService).findById(eq(TICKET_ID), any());
        doReturn(page).when(commentService).findAllBy(eq(TICKET_ID), any());

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/comments", TICKET_ID)
                        .with(user(adminDetails)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.content").isArray(),
                        jsonPath("$.content.length()").value(2),
                        jsonPath("$.metadata.totalElements").value(2)
                );
    }

    @Test
    void shouldReturnEmptyPageWhenTicketHasNoComments() throws Exception {
        PageImpl<CommentResponse> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

        doReturn(Optional.of(TicketResponse.class)).when(ticketService).findById(eq(TICKET_ID), any());
        doReturn(emptyPage).when(commentService).findAllBy(eq(TICKET_ID), any());

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/comments", TICKET_ID)
                        .with(user(adminDetails)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.content").isEmpty(),
                        jsonPath("$.metadata.totalElements").value(0)
                );
    }

    @Test
    void shouldReturnNotFoundWhenTryingToFindCommentsOfNonExistingTicket() throws Exception {
        doReturn(Optional.empty()).when(ticketService).findById(eq(TICKET_ID), any());

        mockMvc.perform(get("/api/v1/tickets/{ticketId}/comments", TICKET_ID)
                        .with(user(adminDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateCommentWhenRequestIsValid() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest("This is a comment");
        CommentResponse comment = mockCommentResponse(COMMENT_ID);

        doReturn(comment).when(commentService).create(eq(TICKET_ID), eq(ADMIN_ID), eq(request.getText()));

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", TICKET_ID)
                        .with(user(adminDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isCreated(),
                        jsonPath("$.id").value(COMMENT_ID)
                );
    }

    @Test
    void shouldReturnBadRequestWhenCreateCommentRequestIsInvalid() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest(null);

        mockMvc.perform(post("/api/v1/tickets/{ticketId}/comments", TICKET_ID)
                        .with(user(adminDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commentService);
    }

    @Test
    void shouldDeleteCommentAndReturnNoContentStatus() throws Exception {
        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}", TICKET_ID, COMMENT_ID)
                        .with(user(adminDetails)))
                .andExpect(status().isNoContent());

        verify(commentService).delete(eq(TICKET_ID), eq(COMMENT_ID), eq(ADMIN_ID));
    }

    @Test
    void shouldReturnNotFoundWhenResourceNotFoundOccurredDuringDelete() throws Exception {
        doThrow(ResourceNotFoundException.class)
                .when(commentService).delete(eq(TICKET_ID), eq(COMMENT_ID), any());

        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}", TICKET_ID, COMMENT_ID)
                        .with(user(adminDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnConflictWhenBusinessRuleViolationOccurredDuringDelete() throws Exception {
        doThrow(BusinessRuleViolationException.class)
                .when(commentService).delete(eq(TICKET_ID), eq(COMMENT_ID), any());

        mockMvc.perform(delete("/api/v1/tickets/{ticketId}/comments/{commentId}", TICKET_ID, COMMENT_ID)
                        .with(user(adminDetails)))
                .andExpect(status().isConflict());
    }

    private TicketFlowUserDetails mockUserDetails(Integer id, Role role) {
        UserEntity user = UserEntity.builder()
                .id(id)
                .email(role.name().toLowerCase() + "@test.com")
                .role(role)
                .build();
        return new TicketFlowUserDetails(user);
    }

    private CommentResponse mockCommentResponse(Long id) {
        return mock(CommentResponse.class, invocation ->
                invocation.getMethod().getName().equals("id") ? id : null);
    }
}
