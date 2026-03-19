package com.rolliedev.ticketflow.integration.http.controller;

import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.testsupport.base.AbstractSpringBootIT;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.collection.IsCollectionWithSize;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@AutoConfigureMockMvc
@RequiredArgsConstructor
class TicketControllerIT extends AbstractSpringBootIT {

    private static final Long TICKET_ID = 1L;
    private static final String ADMIN_ID = "1";
    private static final String AGENT_ID = "2";
    private static final String CUSTOMER_ID = "3";

    private final MockMvc mockMvc;

    @Test
    void shouldFindAllTicketsAndReturnOk() throws Exception {
        mockMvc.perform(get("/tickets"))
                .andExpectAll(
                        model().attributeExists("page", "filter", "statuses", "priorities"),
                        view().name("ticket/list"),
                        status().isOk()
                );
    }

    @Test
    void shouldFindTicketByIdAndReturnOk() throws Exception {
        mockMvc.perform(get("/tickets/{id}", TICKET_ID))
                .andExpectAll(
                        model().attributeExists("ticket", "users", "timeline", "comments"),
                        view().name("ticket/detail"),
                        status().isOk()
                );
    }

    @Test
    void shouldNotFindTicketByIdAndReturnNotFound() throws Exception {
        mockMvc.perform(get("/tickets/{id}", 999999999L))
                .andExpectAll(
                        status().isNotFound(),
                        model().attributeExists("message"),
                        model().attribute("message", ResourceNotFoundException.ticket(999999999L).getMessage()),
                        view().name("error/404")
                );
    }

    @Test
    void shouldRedirectToNewTicketDetailsAfterCreation() throws Exception {
        mockMvc.perform(post("/tickets")
                        .param("title", "test")
                        .param("description", "test")
                        .param("creatorId", CUSTOMER_ID)
                )
                .andExpectAll(
                        status().is3xxRedirection(),
                        redirectedUrlPattern("/tickets/{\\d+}")
                );
    }

    @Test
    void shouldRedirectBackToCreateFormWithErrorsAfterValidationFailureDuringCreate() throws Exception {
        mockMvc.perform(post("/tickets")
                        .param("title", StringUtils.EMPTY)
                        .param("description", StringUtils.EMPTY)
                        .param("creatorId", CUSTOMER_ID)
                )
                .andExpectAll(
                        status().is3xxRedirection(),
                        flash().attributeExists("ticket"),
                        flash().attributeExists("errors"),
                        flash().attribute("errors", IsCollectionWithSize.hasSize(2)),
                        redirectedUrl("/tickets/new")
                );
    }

    @Test
    void shouldAssignAndRedirectBackToTicket() throws Exception {
        mockMvc.perform(post("/tickets/{id}/assign", TICKET_ID)
                        .param("actorId", ADMIN_ID)
                        .param("assigneeId", AGENT_ID))
                .andExpectAll(
                        status().is3xxRedirection(),
                        redirectedUrl("/tickets/" + TICKET_ID)
                );
    }

    @Test
    void shouldThrowExceptionWhenAssignAndRedirectToTicket() throws Exception {
        mockMvc.perform(post("/tickets/{id}/assign", TICKET_ID)
                        .param("actorId", ADMIN_ID)
                        .param("assigneeId", CUSTOMER_ID)
                        .header("Referer", "/tickets/" + TICKET_ID))
                .andExpectAll(
                        status().is3xxRedirection(),
                        flash().attributeExists("error"),
                        flash().attribute("error", "Only agents or admins can be assigned to tickets"),
                        redirectedUrl("/tickets/" + TICKET_ID)
                );
    }
}