package com.rolliedev.ticketflow.integration.http.rest;

import com.rolliedev.ticketflow.dto.AssignTicketRequest;
import com.rolliedev.ticketflow.dto.ChangePriorityRequest;
import com.rolliedev.ticketflow.dto.CreateTicketRequest;
import com.rolliedev.ticketflow.dto.PublicRegistrationRequest;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.SlaStatus;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.testsupport.base.AbstractRestIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TicketRestControllerIT extends AbstractRestIT {

    private UserEntity admin, agent, customer;
    private TicketEntity ticket1, ticket2;

    @BeforeEach
    void setUp() {
        admin = userRepository.findByEmail("lex.luthor@gmail.com").orElseThrow();
        agent = userRepository.findByEmail("bruce.wayne@gmail.com").orElseThrow();
        customer = userRepository.findByEmail("clark.kent@gmail.com").orElseThrow();

        ticket1 = ticketRepository.findById(1L).orElseThrow();
        ticket2 = ticketRepository.findById(2L).orElseThrow();
    }

    @Test
    void shouldReturnTicketWhenAdminRequestsAnyTicket() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}", ticket1.getId())
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticket1.getId()))
                .andExpect(jsonPath("$.title").value("Cannot log in"))
                .andExpect(jsonPath("$.status").value(TicketStatus.IN_PROGRESS.name()))
                .andExpect(jsonPath("$.priority").value(TicketPriority.HIGH.name()))
                .andExpect(jsonPath("$.firstResponseDeadline").exists())
                .andExpect(jsonPath("$.resolutionDeadline").exists())
                .andExpect(jsonPath("$.responseSlaStatus").value(SlaStatus.MET.name()))
                .andExpect(jsonPath("$.resolutionSlaStatus").value(SlaStatus.BREACHED.name()));
    }

    @Test
    void shouldReturnTicketWhenAgentRequestsAnyTicket() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}", ticket1.getId())
                        .with(httpBasic("bruce.wayne@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticket1.getId()));
    }

    @Test
    void shouldReturnTicketWhenCustomerRequestsOwnTicket() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticket1.getId()));
    }

    @Test
    void shouldReturn404WhenCustomerRequestsTicketTheyDoNotOwn() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new PublicRegistrationRequest("Lois", "Lane", "lois.lane@gmail.com", "pass123"))));

        mockMvc.perform(get("/api/v1/tickets/{id}", ticket1.getId())
                        .with(httpBasic("lois.lane@gmail.com", "pass123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404WhenTicketDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}", 999L)
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WhenFetchingTicketWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}", ticket1.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnAllTicketsWhenCalledByAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/tickets")
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.totalElements").value(5))
                .andExpect(jsonPath("$.content.length()").value(5));
    }

    @Test
    void shouldReturnOnlyOwnTicketsWhenCalledByCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/tickets")
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].createdBy.id", everyItem(equalTo(customer.getId()))));
    }

    @Test
    void shouldReturnFilteredResultsWhenStatusFilterIsApplied() throws Exception {
        mockMvc.perform(get("/api/v1/tickets")
                        .with(httpBasic("lex.luthor@gmail.com", "123"))
                        .param("status", TicketStatus.NEW.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].status", everyItem(equalTo(TicketStatus.NEW.name()))));
    }

    @Test
    void shouldReturnFilteredResultsWhenPriorityFilterIsApplied() throws Exception {
        mockMvc.perform(get("/api/v1/tickets")
                        .with(httpBasic("lex.luthor@gmail.com", "123"))
                        .param("priority", TicketPriority.HIGH.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ticket1.getId()));
    }

    @Test
    void shouldReturnFilteredResultsWhenAssigneeFilterIsApplied() throws Exception {
        mockMvc.perform(get("/api/v1/tickets")
                        .with(httpBasic("lex.luthor@gmail.com", "123"))
                        .param("assigneeId", String.valueOf(agent.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].assignedTo.id", everyItem(equalTo(agent.getId()))));
    }

    @Test
    void shouldReturnCorrectPageSizeWhenPageSizeParamIsProvided() throws Exception {
        mockMvc.perform(get("/api/v1/tickets")
                        .with(httpBasic("lex.luthor@gmail.com", "123"))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.page").value(0))
                .andExpect(jsonPath("$.metadata.size").value(2))
                .andExpect(jsonPath("$.metadata.totalElements").value(5))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void shouldReturn401WhenListingTicketsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnTimelineWhenCalledByAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}/events", ticket1.getId())
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.totalElements").value(7))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void shouldReturnTimelineWhenCalledByAgent() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}/events", ticket1.getId())
                        .with(httpBasic("bruce.wayne@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.totalElements").value(7));
    }

    @Test
    void shouldReturnCorrectPageSizeWhenTimelinePageSizeParamIsProvided() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}/events", ticket1.getId())
                        .with(httpBasic("lex.luthor@gmail.com", "123"))
                        .param("page", "0")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.page").value(0))
                .andExpect(jsonPath("$.metadata.size").value(3))
                .andExpect(jsonPath("$.metadata.totalElements").value(7))
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    void shouldReturn404WhenTimelineRequestedForNonExistentTicket() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}/events", 999L)
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404WhenCustomerRequestsTimelineForTicketTheyDoNotOwn() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new PublicRegistrationRequest("Lois", "Lane", "lois.lane@gmail.com", "pass123"))));

        mockMvc.perform(get("/api/v1/tickets/{id}/events", ticket1.getId())
                        .with(httpBasic("lois.lane@gmail.com", "pass123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WhenFetchingTimelineWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/{id}/events", ticket1.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreateTicketAndReturn201WithLocationHeaderWhenCalledByCustomer() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest(
                "Printer not working", "Office printer is offline"
        );

        mockMvc.perform(post("/api/v1/tickets")
                        .with(httpBasic("clark.kent@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/tickets/\\d+")))
                .andExpect(jsonPath("$.title").value("Printer not working"))
                .andExpect(jsonPath("$.description").value("Office printer is offline"))
                .andExpect(jsonPath("$.status").value(TicketStatus.NEW.name()))
                .andExpect(jsonPath("$.priority").value(TicketPriority.MEDIUM.name()))
                .andExpect(jsonPath("$.firstResponseDeadline").exists())
                .andExpect(jsonPath("$.resolutionDeadline").exists())
                .andExpect(jsonPath("$.responseSlaStatus").value(SlaStatus.ON_TRACK.name()))
                .andExpect(jsonPath("$.resolutionSlaStatus").value(SlaStatus.ON_TRACK.name()))
                .andExpect(jsonPath("$.firstRespondedAt").isEmpty())
                .andExpect(jsonPath("$.resolvedAt").isEmpty());
    }

    @Test
    void shouldCreateTicketWhenCalledByAgent() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest("VPN issue", "Cannot connect to VPN");

        mockMvc.perform(post("/api/v1/tickets")
                        .with(httpBasic("bruce.wayne@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("VPN issue"));
    }

    @Test
    void shouldReturn400WhenCreatingTicketWithBlankTitle() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest("", "Some description");

        mockMvc.perform(post("/api/v1/tickets")
                        .with(httpBasic("clark.kent@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenCreatingTicketWithMissingBody() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .with(httpBasic("clark.kent@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn401WhenCreatingTicketWithoutAuthentication() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest("Title", "Description");

        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAssignTicketWhenCalledByAdmin() throws Exception {
        AssignTicketRequest request = new AssignTicketRequest(agent.getId());

        mockMvc.perform(patch("/api/v1/tickets/{id}/assign", ticket2.getId())
                        .with(httpBasic("lex.luthor@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticket2.getId()))
                .andExpect(jsonPath("$.assignedTo.id").value(agent.getId()));
    }

    @Test
    void shouldReassignTicketWhenCalledByCurrentAssignee() throws Exception {
        // ticket1 is assigned to agent — agent can reassign to admin
        AssignTicketRequest request = new AssignTicketRequest(admin.getId());

        mockMvc.perform(patch("/api/v1/tickets/{id}/assign", ticket1.getId())
                        .with(httpBasic("bruce.wayne@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo.id").value(admin.getId()));
    }

    @Test
    void shouldReturn403WhenCustomerTriesToAssignTicket() throws Exception {
        AssignTicketRequest request = new AssignTicketRequest(agent.getId());

        mockMvc.perform(patch("/api/v1/tickets/{id}/assign", ticket2.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn400WhenAssigningTicketToCustomer() throws Exception {
        AssignTicketRequest request = new AssignTicketRequest(customer.getId());

        mockMvc.perform(patch("/api/v1/tickets/{id}/assign", ticket2.getId())
                        .with(httpBasic("lex.luthor@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenAssigneeIdIsMissingInRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/assign", ticket2.getId())
                        .with(httpBasic("lex.luthor@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn409WhenAssigningClosedTicket() throws Exception {
        ticket2.setStatus(TicketStatus.CLOSED);
        ticketRepository.save(ticket2);
        flushAndClear();

        AssignTicketRequest request = new AssignTicketRequest(agent.getId());

        mockMvc.perform(patch("/api/v1/tickets/{id}/assign", ticket2.getId())
                        .with(httpBasic("lex.luthor@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldStartProgressAndAutoAssignWhenTicketIsUnassigned() throws Exception {
        // ticket2 is NEW and unassigned — agent picks it up automatically
        mockMvc.perform(patch("/api/v1/tickets/{id}/start", ticket2.getId())
                        .with(httpBasic("bruce.wayne@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(TicketStatus.IN_PROGRESS.name()))
                .andExpect(jsonPath("$.assignedTo.id").value(agent.getId()));
    }

    @Test
    void shouldStartProgressWhenCalledByAssignedAgent() throws Exception {
        ticket1.setStatus(TicketStatus.NEW);
        ticketRepository.save(ticket1);
        flushAndClear();

        mockMvc.perform(patch("/api/v1/tickets/{id}/start", ticket1.getId())
                        .with(httpBasic("bruce.wayne@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(TicketStatus.IN_PROGRESS.name()));
    }

    @Test
    void shouldReturn403WhenCustomerTriesToStartProgress() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/start", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn404WhenStartingProgressOnNonExistentTicket() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/start", 999L)
                        .with(httpBasic("bruce.wayne@gmail.com", "123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WhenStartingProgressWithoutAuthentication() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/start", ticket1.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldTransitionToWaitingCustomerWhenAssignedAgentRequestsInfo() throws Exception {
        // ticket1 is IN_PROGRESS, assigned to bruce.wayne
        mockMvc.perform(patch("/api/v1/tickets/{id}/request-info", ticket1.getId())
                        .with(httpBasic("bruce.wayne@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(TicketStatus.WAITING_CUSTOMER.name()));
    }

    @Test
    void shouldTransitionToWaitingCustomerWhenCalledByAdmin() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/request-info", ticket1.getId())
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(TicketStatus.WAITING_CUSTOMER.name()));
    }

    @Test
    void shouldReturn422WhenRequestingInfoOnTicketWithInvalidStatusTransition() throws Exception {
        // ticket2 is NEW — NEW → WAITING_CUSTOMER is not a valid transition
        mockMvc.perform(patch("/api/v1/tickets/{id}/request-info", ticket2.getId())
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void shouldReturn403WhenCustomerTriesToRequestInfo() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/request-info", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldResolveTicketWhenCalledByAssignedAgent() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/resolve", ticket1.getId())
                        .with(httpBasic("bruce.wayne@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(TicketStatus.RESOLVED.name()));
    }

    @Test
    void shouldResolveTicketWhenCalledByAdmin() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/resolve", ticket1.getId())
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(TicketStatus.RESOLVED.name()));
    }

    @Test
    void shouldReturn403WhenCustomerTriesToResolveTicket() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/resolve", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn404WhenResolvingNonExistentTicket() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/resolve", 999L)
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCloseTicketWhenCustomerClosesResolvedTicket() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/resolve", ticket1.getId())
                        .with(httpBasic("bruce.wayne@gmail.com", "123")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/tickets/{id}/close", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(TicketStatus.CLOSED.name()));
    }

    @Test
    void shouldReturn422WhenCustomerClosesTicketWithInvalidStatusTransition() throws Exception {
        // ticket1 is IN_PROGRESS — IN_PROGRESS → CLOSED is not valid
        mockMvc.perform(patch("/api/v1/tickets/{id}/close", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void shouldReturn403WhenAgentTriesToCloseTicket() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/close", ticket1.getId())
                        .with(httpBasic("bruce.wayne@gmail.com", "123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn403WhenNonOwnerCustomerTriesToCloseTicket() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new PublicRegistrationRequest("Lois", "Lane", "lois.lane@gmail.com", "pass123"))));

        mockMvc.perform(patch("/api/v1/tickets/{id}/close", ticket1.getId())
                        .with(httpBasic("lois.lane@gmail.com", "pass123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldChangePriorityWhenCalledByAssignedAgent() throws Exception {
        ChangePriorityRequest request = new ChangePriorityRequest(TicketPriority.LOW);

        mockMvc.perform(patch("/api/v1/tickets/{id}/priority", ticket1.getId())
                        .with(httpBasic("bruce.wayne@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value(TicketPriority.LOW.name()));
    }

    @Test
    void shouldChangePriorityWhenCalledByAdmin() throws Exception {
        ChangePriorityRequest request = new ChangePriorityRequest(TicketPriority.MEDIUM);

        mockMvc.perform(patch("/api/v1/tickets/{id}/priority", ticket1.getId())
                        .with(httpBasic("lex.luthor@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value(TicketPriority.MEDIUM.name()));
    }

    @Test
    void shouldReturn403WhenCustomerTriesToChangePriority() throws Exception {
        ChangePriorityRequest request = new ChangePriorityRequest(TicketPriority.LOW);

        mockMvc.perform(patch("/api/v1/tickets/{id}/priority", ticket1.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn400WhenPriorityFieldIsMissingInRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/tickets/{id}/priority", ticket1.getId())
                        .with(httpBasic("bruce.wayne@gmail.com", "123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn401WhenChangingPriorityWithoutAuthentication() throws Exception {
        ChangePriorityRequest request = new ChangePriorityRequest(TicketPriority.LOW);

        mockMvc.perform(patch("/api/v1/tickets/{id}/priority", ticket1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
