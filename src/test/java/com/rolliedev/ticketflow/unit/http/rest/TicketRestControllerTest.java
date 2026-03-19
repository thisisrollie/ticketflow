package com.rolliedev.ticketflow.unit.http.rest;

import com.rolliedev.ticketflow.dto.ActorCommand;
import com.rolliedev.ticketflow.dto.AssignTicketRequest;
import com.rolliedev.ticketflow.dto.CreateTicketRequest;
import com.rolliedev.ticketflow.dto.TicketEventResponse;
import com.rolliedev.ticketflow.dto.TicketResponse;
import com.rolliedev.ticketflow.dto.UserSummary;
import com.rolliedev.ticketflow.entity.enums.TicketEventType;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.exception.InvalidStatusTransitionException;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.exception.TicketFlowAccessDeniedException;
import com.rolliedev.ticketflow.http.handler.RestControllerExceptionHandler;
import com.rolliedev.ticketflow.http.rest.TicketRestController;
import com.rolliedev.ticketflow.service.TicketEventService;
import com.rolliedev.ticketflow.service.TicketService;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketRestController.class)
@Import(RestControllerExceptionHandler.class)
class TicketRestControllerTest {

    private static final Long TICKET_ID = 1L;
    private static final Integer ADMIN_ID = 1;
    private static final Integer AGENT_ID = 2;
    private static final Integer CUSTOMER_ID = 3;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private TicketEventService eventService;

    @Test
    void shouldFindByTicketId() throws Exception {
        doReturn(Optional.of(ticketResponse())).when(ticketService).findById(TICKET_ID);

        mockMvc.perform(get("/api/v1/tickets/{id}", TICKET_ID))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET_ID),
                        jsonPath("$.title").value("Cannot log in"),
                        jsonPath("$.description").value("Getting error when logging in with Google"),
                        jsonPath("$.status").value("IN_PROGRESS"),
                        jsonPath("$.priority").value("HIGH")
                );

        verify(ticketService).findById(TICKET_ID);
    }

    @Test
    void shouldReturnNotFoundWhenTicketDoesNotExist() throws Exception {
        doReturn(Optional.empty()).when(ticketService).findById(TICKET_ID);

        mockMvc.perform(get("/api/v1/tickets/{id}", TICKET_ID))
                .andExpectAll(
                        status().isNotFound(),
                        jsonPath("$.message").value(ResourceNotFoundException.ticket(TICKET_ID).getMessage())
                );

        verify(ticketService).findById(TICKET_ID);
    }

    @Test
    void shouldFindAllTickets() throws Exception {
        Page<TicketResponse> page = new PageImpl<>(List.of(ticketResponse()), PageRequest.of(0, 10), 1);
        doReturn(page).when(ticketService).findAll(any(), any());

        mockMvc.perform(get("/api/v1/tickets")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.content").value(hasSize(1)),
                        jsonPath("$.content[0].id").value(TICKET_ID),
                        jsonPath("$.content[0].title").value("Cannot log in")
                );

        verify(ticketService).findAll(any(), any());
    }

    @Test
    void shouldGetTimeline() throws Exception {
        Page<TicketEventResponse> page = new PageImpl<>(List.of(ticketEventResponse()), PageRequest.of(0, 10), 1);
        doReturn(page).when(eventService).getTimeline(eq(TICKET_ID), any(Pageable.class));

        mockMvc.perform(get("/api/v1/tickets/{id}/events", TICKET_ID)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.content").value(Matchers.hasSize(1)),
                        jsonPath("$.content[0].id").value(10L),
                        jsonPath("$.content[0].eventType").value("CREATED")
                );

        verify(eventService).getTimeline(eq(TICKET_ID), any(Pageable.class));
    }

    @Test
    void shouldCreateTicket() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest(
                "Cannot log in",
                "Getting error when logging in with Google",
                CUSTOMER_ID
        );
        doReturn(ticketResponse()).when(ticketService).create(any(CreateTicketRequest.class));

        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isCreated(),
                        jsonPath("$.id").value(TICKET_ID),
                        jsonPath("$.title").value("Cannot log in")
                );

        verify(ticketService).create(any(CreateTicketRequest.class));
    }

    @Test
    void shouldReturnBadRequestWhenCreateTicketIsInvalid() throws Exception {
        CreateTicketRequest invalidRequest = new CreateTicketRequest(StringUtils.EMPTY, StringUtils.EMPTY, null);

        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpectAll(
                        status().isBadRequest()
                );

        verify(ticketService, never()).create(any(CreateTicketRequest.class));
    }

    @Test
    void shouldAssignTicket() throws Exception {
        AssignTicketRequest request = new AssignTicketRequest(ADMIN_ID, AGENT_ID);

        doReturn(ticketResponse()).when(ticketService).assign(TICKET_ID, ADMIN_ID, AGENT_ID);

        mockMvc.perform(patch("/api/v1/tickets/{id}/assign", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET_ID)
                );

        verify(ticketService).assign(TICKET_ID, ADMIN_ID, AGENT_ID);
    }

    @Test
    void shouldReturnBadRequestWhenAssignTicketIsInvalid() throws Exception {
        AssignTicketRequest invalidRequest = new AssignTicketRequest(null, null);

        mockMvc.perform(patch("/api/v1/tickets/{id}/assign", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpectAll(
                        status().isBadRequest()
                );

        verify(ticketService, never()).assign(any(), any(), any());
    }

    @Test
    void shouldReturnUnprocessableContentWhenResolvingNewTicket() throws Exception {
        ActorCommand request = new ActorCommand(AGENT_ID);

        doThrow(new InvalidStatusTransitionException(TicketStatus.NEW, TicketStatus.RESOLVED))
                .when(ticketService).resolve(TICKET_ID, AGENT_ID);

        mockMvc.perform(patch("/api/v1/tickets/{id}/resolve", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isUnprocessableContent(),
                        jsonPath("$.message").value(
                                new InvalidStatusTransitionException(TicketStatus.NEW, TicketStatus.RESOLVED).getMessage()
                        )
                );

        verify(ticketService).resolve(TICKET_ID, AGENT_ID);
    }

    @Test
    void shouldReturnForbiddenWhenClosingTicketAsAgent() throws Exception {
        ActorCommand request = new ActorCommand(AGENT_ID);

        doThrow(new TicketFlowAccessDeniedException("Only customers can manually close tickets"))
                .when(ticketService).closeByCustomer(TICKET_ID, AGENT_ID);

        mockMvc.perform(patch("/api/v1/tickets/{id}/close", TICKET_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isForbidden(),
                        jsonPath("$.message").value("Only customers can manually close tickets")
                );

        verify(ticketService).closeByCustomer(TICKET_ID, AGENT_ID);
    }

    private TicketResponse ticketResponse() {
        return new TicketResponse(
                TICKET_ID,
                "Cannot log in",
                "Getting error when logging in with Google",
                TicketStatus.IN_PROGRESS,
                TicketPriority.HIGH,
                userSummary(CUSTOMER_ID, "Clark Kent"),
                userSummary(AGENT_ID, "Bruce Wayne"),
                Instant.parse("2026-03-17T10:00:00Z"),
                Instant.parse("2026-03-17T11:30:00Z"),
                null
        );
    }

    private UserSummary userSummary(Integer id, String fullName) {
        return new UserSummary(id, fullName);
    }

    private TicketEventResponse ticketEventResponse() {
        return new TicketEventResponse(
                10L,
                userSummary(CUSTOMER_ID, "Clark Kent"),
                TicketEventType.CREATED,
                Map.of("ticketId", TICKET_ID, "createdById", CUSTOMER_ID),
                Instant.parse("2026-03-17T10:00:00Z")
        );
    }
}



