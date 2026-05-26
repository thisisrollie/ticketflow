package com.rolliedev.ticketflow.unit.http.rest;

import com.rolliedev.ticketflow.config.SecurityConfiguration;
import com.rolliedev.ticketflow.dto.AssignTicketRequest;
import com.rolliedev.ticketflow.dto.ChangePriorityRequest;
import com.rolliedev.ticketflow.dto.CreateTicketRequest;
import com.rolliedev.ticketflow.dto.TicketEventResponse;
import com.rolliedev.ticketflow.dto.TicketResponse;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.SlaStatus;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.http.rest.TicketRestController;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.service.TicketEventService;
import com.rolliedev.ticketflow.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketRestController.class)
@Import(SecurityConfiguration.class)
public class TicketRestControllerTest {

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

    private TicketFlowUserDetails adminDetails;
    private TicketFlowUserDetails agentDetails;
    private TicketFlowUserDetails customerDetails;

    @BeforeEach
    void setUp() {
        adminDetails = mockUserDetails(ADMIN_ID, Role.ADMIN);
        agentDetails = mockUserDetails(AGENT_ID, Role.AGENT);
        customerDetails = mockUserDetails(CUSTOMER_ID, Role.CUSTOMER);
    }

    @Test
    void shouldReturnTicketWhenTicketExists() throws Exception {
        TicketResponse ticket = mockTicketResponse(TICKET_ID);

        doReturn(Optional.of(ticket)).when(ticketService).findById(eq(TICKET_ID), any());

        mockMvc.perform(get("/api/v1/tickets/{id}", TICKET_ID)
                        .with(user(adminDetails)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET_ID),
                        jsonPath("$.responseSlaStatus").value(SlaStatus.ON_TRACK.name()),
                        jsonPath("$.resolutionSlaStatus").value(SlaStatus.ON_TRACK.name()),
                        jsonPath("$.firstResponseDeadline").exists(),
                        jsonPath("$.resolutionDeadline").exists()
                );
    }

    @Test
    void shouldReturnNotFoundWhenTicketDoesNotExist() throws Exception {
        doReturn(Optional.empty()).when(ticketService).findById(eq(TICKET_ID), any());

        mockMvc.perform(get("/api/v1/tickets/{id}", TICKET_ID)
                        .with(user(adminDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnUnauthorizedWhenUserIsNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}", TICKET_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnPagedTicketsWhenTicketsExist() throws Exception {
        TicketResponse ticket = mockTicketResponse(TICKET_ID);
        Page<TicketResponse> page = new PageImpl<>(List.of(ticket), PageRequest.of(0, 10), 1);

        doReturn(page).when(ticketService).findAll(any(), any(), any());

        mockMvc.perform(get("/api/v1/tickets")
                        .with(user(adminDetails))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.content").isArray(),
                        jsonPath("$.content.length()").value(1),
                        jsonPath("$.metadata.totalElements").value(1)
                );
    }

    @Test
    void shouldReturnEmptyPageWhenNoTicketsExist() throws Exception {
        Page<TicketResponse> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

        doReturn(emptyPage).when(ticketService).findAll(any(), any(), any());

        mockMvc.perform(get("/api/v1/tickets")
                        .with(user(adminDetails)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.content").isEmpty(),
                        jsonPath("$.metadata.totalElements").value(0)
                );
    }

    @Test
    void shouldReturnPagedTicketEventsOfGivenTicket() throws Exception {
        Page<TicketEventResponse> eventsPage = new PageImpl<>(
                List.of(mock(TicketEventResponse.class)),
                PageRequest.of(0, 20), 1);

        doReturn(Optional.of(mock(TicketResponse.class))).when(ticketService).findById(eq(TICKET_ID), any());
        doReturn(eventsPage).when(eventService).getTimeline(eq(TICKET_ID), any());

        mockMvc.perform(get("/api/v1/tickets/{id}/events", TICKET_ID)
                        .with(user(adminDetails)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.content").isArray(),
                        jsonPath("$.content.length()").value(1),
                        jsonPath("$.metadata.totalElements").value(1)
                );
    }

    @Test
    void shouldReturnEmptyPageWhenTicketHasNoEvents() throws Exception {
        PageImpl<Object> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);

        doReturn(Optional.of(mock(TicketResponse.class))).when(ticketService).findById(eq(TICKET_ID), any());
        doReturn(emptyPage).when(eventService).getTimeline(eq(TICKET_ID), any());

        mockMvc.perform(get("/api/v1/tickets/{id}/events", TICKET_ID)
                        .with(user(adminDetails)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.content").isEmpty(),
                        jsonPath("$.metadata.totalElements").value(0)
                );
    }

    @Test
    void shouldReturnNotFoundWhenTryingToGetTimelineOfNonExistingTicket() throws Exception {
        doReturn(Optional.empty()).when(ticketService).findById(eq(TICKET_ID), any());

        mockMvc.perform(get("/api/v1/tickets/{id}/events", TICKET_ID)
                        .with(user(adminDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateTicketAndReturnLocationHeaderWhenRequestIsValid() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest("Cannot login", "Getting error");
        TicketResponse createdTicket = mockTicketResponse(TICKET_ID);

        doReturn(createdTicket).when(ticketService).create(any(), eq(ADMIN_ID));

        mockMvc.perform(post("/api/v1/tickets")
                        .with(user(adminDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isCreated(),
                        header().string("Location", "/api/v1/tickets/" + TICKET_ID),
                        jsonPath("$.id").value(TICKET_ID)
                );
    }

    @Test
    void shouldReturnBadRequestWhenCreateTicketRequestIsInvalid() throws Exception {
        CreateTicketRequest invalidRequest = new CreateTicketRequest(null, null);

        mockMvc.perform(post("/api/v1/tickets")
                        .with(user(customerDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldAssignTicketAndReturnUpdatedTicketWhenRequestIsValid() throws Exception {
        AssignTicketRequest request = new AssignTicketRequest(AGENT_ID);
        TicketResponse updatedTicket = mockTicketResponse(TICKET_ID);

        doReturn(updatedTicket).when(ticketService).assign(eq(TICKET_ID), eq(ADMIN_ID), eq(AGENT_ID));

        mockMvc.perform(patch("/api/v1/tickets/{id}/assign", TICKET_ID)
                        .with(user(adminDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET_ID)
                );
    }

    @Test
    void shouldReturnBadRequestWhenAssigneeIdIsMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/assign", TICKET_ID)
                        .with(user(adminDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ticketService);
    }

    @Test
    void shouldStartProgressAndReturnUpdatedTicketWhenActorIsAgent() throws Exception {
        TicketResponse updatedTicket = mockTicketResponse(TICKET_ID);

        doReturn(updatedTicket).when(ticketService).startProgress(eq(TICKET_ID), eq(AGENT_ID));

        mockMvc.perform(patch("/api/v1/tickets/{id}/start", TICKET_ID)
                        .with(user(agentDetails)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET_ID)
                );
    }

    @Test
    void shouldRequestCustomerInfoAndReturnUpdatedTicketWhenActorIsAgent() throws Exception {
        TicketResponse updatedTicket = mockTicketResponse(TICKET_ID);

        doReturn(updatedTicket).when(ticketService).requestCustomerInfo(eq(TICKET_ID), eq(AGENT_ID));

        mockMvc.perform(patch("/api/v1/tickets/{id}/request-info", TICKET_ID)
                        .with(user(agentDetails)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET_ID)
                );
    }

    @Test
    void shouldResolveTicketAndReturnUpdatedTicketWhenActorIsAgent() throws Exception {
        TicketResponse updatedTicket = mockTicketResponse(TICKET_ID);

        doReturn(updatedTicket).when(ticketService).resolve(eq(TICKET_ID), eq(AGENT_ID));

        mockMvc.perform(patch("/api/v1/tickets/{id}/resolve", TICKET_ID)
                        .with(user(agentDetails)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET_ID)
                );
    }

    @Test
    void shouldCloseTicketAndReturnUpdatedTicketWhenActorIsCustomer() throws Exception {
        TicketResponse updatedTicket = mockTicketResponse(TICKET_ID);

        doReturn(updatedTicket).when(ticketService).closeByCustomer(eq(TICKET_ID), eq(CUSTOMER_ID));

        mockMvc.perform(patch("/api/v1/tickets/{id}/close", TICKET_ID)
                        .with(user(customerDetails)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET_ID)
                );
    }

    @Test
    void shouldChangePriorityAndReturnUpdatedTicketWhenRequestIsValid() throws Exception {
        ChangePriorityRequest request = new ChangePriorityRequest(TicketPriority.HIGH);
        TicketResponse updatedTicket = mockTicketResponse(TICKET_ID);

        doReturn(updatedTicket).when(ticketService).changePriority(eq(TICKET_ID), eq(AGENT_ID), eq(TicketPriority.HIGH));

        mockMvc.perform(patch("/api/v1/tickets/{id}/priority", TICKET_ID)
                        .with(user(agentDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET_ID)
                );
    }

    @Test
    void shouldReturnBadRequestWhenPriorityIsMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/priority", TICKET_ID)
                        .with(user(agentDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ticketService);
    }

    private TicketFlowUserDetails mockUserDetails(Integer id, Role role) {
        UserEntity user = UserEntity.builder()
                .id(id)
                .email(role.name().toLowerCase() + "@test.com")
                .role(role)
                .build();
        return new TicketFlowUserDetails(user);
    }

    private TicketResponse mockTicketResponse(Long id) {
        Instant createdAt = Instant.parse("2026-05-10T10:00:00Z");

        return new TicketResponse(
                id,
                "Test ticket",
                "Test description",
                TicketStatus.NEW,
                TicketPriority.MEDIUM,
                null,
                null,
                createdAt,
                createdAt,
                null,
                null,
                createdAt.plus(1, ChronoUnit.DAYS),
                createdAt.plus(3, ChronoUnit.DAYS),
                SlaStatus.ON_TRACK,
                SlaStatus.ON_TRACK
        );
    }
}
