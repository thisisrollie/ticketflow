package com.rolliedev.ticketflow.integration.http.rest;

import com.rolliedev.ticketflow.dto.ActorCommand;
import com.rolliedev.ticketflow.dto.AssignTicketRequest;
import com.rolliedev.ticketflow.dto.ChangePriorityRequest;
import com.rolliedev.ticketflow.dto.CreateTicketRequest;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.exception.InvalidStatusTransitionException;
import com.rolliedev.ticketflow.service.TicketService;
import com.rolliedev.ticketflow.testsupport.base.AbstractSpringBootIT;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.StringContains.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@RequiredArgsConstructor
class TicketRestControllerIT extends AbstractSpringBootIT {

    private static final Long TICKET1_ID = 1L;
    private static final Long TICKET2_ID = 2L;

    private static final Integer ADMIN_ID = 1;
    private static final Integer AGENT_ID = 2;
    private static final Integer CUSTOMER_ID = 3;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final TicketService ticketService;

    @Test
    void shouldFindTicketById() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}", TICKET1_ID))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET1_ID),
                        jsonPath("$.title").value("Cannot log in"),
                        jsonPath("$.description").value("Getting error when logging in with Google"),
                        jsonPath("$.status").value(TicketStatus.IN_PROGRESS.name()),
                        jsonPath("$.priority").value(TicketPriority.HIGH.name())
                );
    }

    @Test
    void shouldReturnNotFoundWhenTicketDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}", 999999))
                .andExpectAll(
                        status().isNotFound(),
                        MockMvcResultMatchers.jsonPath("$.message").value(containsStringIgnoringCase("not found"))
                );
    }

    @Test
    void shouldFindAllTickets() throws Exception {
        mockMvc.perform(get("/api/v1/tickets")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "id,asc"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.content").value(hasSize(3)),
                        jsonPath("$.content[0].id").value(TICKET1_ID),
                        jsonPath("$.content[0].title").value("Cannot log in")
                );
    }

    @Test
    void shouldGetTimeline() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}/events", TICKET1_ID)
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "id,desc"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.content").value(hasSize(6)),
                        jsonPath("$.content[0].eventType").value("COMMENTED")
                );
    }

    @Test
    void shouldCreateTicket() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest("test", "test", CUSTOMER_ID);

        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isCreated(),
                        jsonPath("$.id").exists(),
                        jsonPath("$.title").value("test"),
                        jsonPath("$.description").value("test"),
                        jsonPath("$.createdBy.id").value(CUSTOMER_ID)
                );
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
    }

    @Test
    void shouldAssignTicket() throws Exception {
        AssignTicketRequest request = new AssignTicketRequest(ADMIN_ID, AGENT_ID);

        mockMvc.perform(patch("/api/v1/tickets/{id}/assign", TICKET2_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET2_ID),
                        jsonPath("$.assignedTo.id").value(AGENT_ID)
                );
    }

    @Test
    void shouldReturnBadRequestWhenAssignTicketIsInvalid() throws Exception {
        AssignTicketRequest invalidRequest = new AssignTicketRequest(null, null);

        mockMvc.perform(patch("/api/v1/tickets/{id}/assign", TICKET2_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpectAll(
                        status().isBadRequest()
                );
    }

    @Test
    void shouldReturnForbiddenWhenAssignTicketToCustomer() throws Exception {
        AssignTicketRequest request = new AssignTicketRequest(ADMIN_ID, CUSTOMER_ID);

        mockMvc.perform(patch("/api/v1/tickets/{id}/assign", TICKET2_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isForbidden(),
                        jsonPath("$.message").value("Only agents or admins can be assigned to tickets")
                );
    }

    @Test
    void shouldReturnConflictWhenAssignClosedTicket() throws Exception {
        ticketService.resolve(TICKET1_ID, AGENT_ID);
        ticketService.closeByCustomer(TICKET1_ID, CUSTOMER_ID);

        AssignTicketRequest request = new AssignTicketRequest(AGENT_ID, AGENT_ID);

        mockMvc.perform(patch("/api/v1/tickets/{id}/assign", TICKET1_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isConflict(),
                        jsonPath("$.message").value("Closed tickets cannot be assigned")
                );
    }

    @Test
    void shouldStartProgress() throws Exception {
        ActorCommand request = new ActorCommand(AGENT_ID);

        mockMvc.perform(patch("/api/v1/tickets/{id}/start", TICKET2_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET2_ID),
                        jsonPath("$.status").value(TicketStatus.IN_PROGRESS.name())
                );
    }

    @Test
    void shouldRequestCustomerInfo() throws Exception {
        ActorCommand request = new ActorCommand(AGENT_ID);

        mockMvc.perform(patch("/api/v1/tickets/{id}/request-info", TICKET1_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET1_ID),
                        jsonPath("$.status").value(TicketStatus.WAITING_CUSTOMER.name())
                );
    }

    @Test
    void shouldResolveTicket() throws Exception {
        ActorCommand request = new ActorCommand(AGENT_ID);

        mockMvc.perform(patch("/api/v1/tickets/{id}/resolve", TICKET1_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET1_ID),
                        jsonPath("$.status").value(TicketStatus.RESOLVED.name())
                );
    }

    @Test
    void shouldReturnUnprocessableContentWhenResolvingNewTicket() throws Exception {
        ActorCommand request = new ActorCommand(AGENT_ID);

        String expectedErrorMessage = new InvalidStatusTransitionException(TicketStatus.NEW, TicketStatus.RESOLVED).getMessage();
        mockMvc.perform(patch("/api/v1/tickets/{id}/resolve", TICKET2_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isUnprocessableContent(),
                        jsonPath("$.message").value(expectedErrorMessage)
                );
    }

    @Test
    void shouldCloseTicket() throws Exception {
        // first resolve a ticket and only then we can close it
        ticketService.resolve(TICKET1_ID, AGENT_ID);

        ActorCommand request = new ActorCommand(CUSTOMER_ID);

        mockMvc.perform(patch("/api/v1/tickets/{id}/close", TICKET1_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET1_ID),
                        jsonPath("$.status").value(TicketStatus.CLOSED.name())
                );
    }

    @Test
    void shouldReturnForbiddenWhenNotCustomerTryToCloseTicket() throws Exception {
        ticketService.resolve(TICKET1_ID, AGENT_ID);

        ActorCommand request = new ActorCommand(AGENT_ID);

        mockMvc.perform(patch("/api/v1/tickets/{id}/close", TICKET1_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isForbidden()
                );
    }

    @Test
    void shouldChangePriority() throws Exception {
        ChangePriorityRequest request = new ChangePriorityRequest(AGENT_ID, TicketPriority.HIGH);

        mockMvc.perform(patch("/api/v1/tickets/{id}/priority", TICKET2_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(TICKET2_ID),
                        jsonPath("$.priority").value(request.getNewPriority().name())
                );
    }

    @Test
    void shouldReturnBadRequestWhenChangePriorityIsInvalid() throws Exception {
        ChangePriorityRequest invalidRequest = new ChangePriorityRequest(null, null);

        mockMvc.perform(patch("/api/v1/tickets/{id}/priority", TICKET2_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpectAll(
                        status().isBadRequest()
                );
    }
}