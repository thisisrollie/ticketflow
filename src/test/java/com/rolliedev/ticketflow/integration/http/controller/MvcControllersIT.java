package com.rolliedev.ticketflow.integration.http.controller;

import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.testsupport.base.AbstractRestIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

public class MvcControllersIT extends AbstractRestIT {

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
    void shouldReturnLoginPageWhenAccessedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/login"));
    }

    @Test
    void shouldRedirectToLoginWhenUnauthenticatedUserAccessesTickets() throws Exception {
        mockMvc.perform(get("/tickets"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void shouldRedirectToLoginWhenUnauthenticatedUserAccessesUsers() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void shouldRedirectToLoginWhenUnauthenticatedUserAccessesAdminPanel() throws Exception {
        mockMvc.perform(get("/admin/users/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void shouldReturnRegistrationPageWhenGetRequestIsMade() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/registration"))
                .andExpect(model().attributeExists("user"));
    }

    @Test
    void shouldRedirectToLoginWhenRegistrationIsSuccessful() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("firstName", "Hal")
                        .param("lastName", "Jordan")
                        .param("email", "hal.jordan@gmail.com")
                        .param("rawPassword", "123test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void shouldRedirectBackToRegistrationWhenFormValidationFails() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("firstName", "")
                        .param("lastName", "")
                        .param("email", "not-an-email")
                        .param("rawPassword", ""))
                .andExpectAll(
                        status().is3xxRedirection(),
                        flash().attributeExists("user"),
                        flash().attributeExists("errors"),
                        flash().attribute("errors", hasSize(4)),
                        redirectedUrl("/register")
                );
    }

    @Test
    void shouldRedirectBackToRegistrationWhenEmailAlreadyExists() throws Exception {
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .header(HttpHeaders.REFERER, "/register")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("firstName", "Clark")
                        .param("lastName", "Kent")
                        .param("email", "clark.kent@gmail.com")
                        .param("rawPassword", "somePassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"));
    }

    @Test
    void shouldReturnUserListPageWhenCalledByAdmin() throws Exception {
        mockMvc.perform(get("/users")
                        .with(user(new TicketFlowUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("user/list"))
                .andExpect(model().attributeExists("page"));
    }

    @Test
    void shouldReturn403WhenCustomerAccessesUserList() throws Exception {
        mockMvc.perform(get("/users")
                        .with(user(new TicketFlowUserDetails(customer))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnUserDetailPageWhenAdminRequestsAnyUser() throws Exception {
        mockMvc.perform(get("/users/{id}", customer.getId())
                        .with(user(new TicketFlowUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("user/detail"))
                .andExpect(model().attributeExists("user"));
    }

    @Test
    void shouldReturnUserDetailPageWhenCustomerRequestsOwnProfile() throws Exception {
        mockMvc.perform(get("/users/{id}", customer.getId())
                        .with(user(new TicketFlowUserDetails(customer))))
                .andExpect(status().isOk())
                .andExpect(view().name("user/detail"));
    }

    @Test
    void shouldReturn404WhenCustomerRequestsAnotherUsersProfile() throws Exception {
        mockMvc.perform(get("/users/{id}", admin.getId())
                        .with(user(new TicketFlowUserDetails(customer))))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnUserCreateFormWhenCalledByAdmin() throws Exception {
        mockMvc.perform(get("/admin/users/new")
                        .with(user(new TicketFlowUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/user-create"))
                .andExpect(model().attributeExists("user", "roles"));
    }

    @Test
    void shouldReturn403WhenAgentAccessesAdminPanel() throws Exception {
        mockMvc.perform(get("/admin/users/new")
                        .with(user(new TicketFlowUserDetails(agent))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRedirectToUsersWhenInternalUserIsCreatedSuccessfully() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .with(csrf())
                        .with(user(new TicketFlowUserDetails(admin)))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("firstName", "Barry")
                        .param("lastName", "Allen")
                        .param("email", "barry.allen@gmail.com")
                        .param("rawPassword", "securePass123")
                        .param("role", "AGENT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"));
    }

    @Test
    void shouldRedirectBackToFormWhenInternalUserCreationValidationFails() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .with(csrf())
                        .with(user(new TicketFlowUserDetails(admin)))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("firstName", "")
                        .param("lastName", "")
                        .param("email", "not-an-email")
                        .param("rawPassword", "")
                        .param("role", ""))
                .andExpectAll(
                        status().is3xxRedirection(),
                        flash().attributeExists("errors"),
                        flash().attribute("errors", hasSize(5)),
                        redirectedUrl("/admin/users/new")
                );
    }

    @Test
    void shouldReturnTicketListPageWhenCalledByAdmin() throws Exception {
        mockMvc.perform(get("/tickets")
                        .with(user(new TicketFlowUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("ticket/list"))
                .andExpect(model().attributeExists(
                        "page", "filter", "filterQueryParams", "statuses",
                        "priorities", "assignees", "responseSlaStatuses", "resolutionSlaStatuses"
                ));
    }

    @Test
    void shouldIncludeInternalFiltersInModelWhenCalledByInternalUser() throws Exception {
        mockMvc.perform(get("/tickets")
                        .with(user(new TicketFlowUserDetails(agent))))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists(
                        "assignees",
                        "responseSlaStatuses",
                        "resolutionSlaStatuses"
                ));
    }

    @Test
    void shouldNotIncludeInternalFiltersInModelWhenCalledByCustomer() throws Exception {
        mockMvc.perform(get("/tickets")
                        .with(user(new TicketFlowUserDetails(customer))))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist(
                        "assignees",
                        "responseSlaStatuses",
                        "resolutionSlaStatuses"
                ));
    }

    @Test
    void shouldReturnTicketDetailPageWhenTicketExistsAndUserHasAccess() throws Exception {
        mockMvc.perform(get("/tickets/{id}", ticket1.getId())
                        .with(user(new TicketFlowUserDetails(admin))))
                .andExpect(status().isOk())
                .andExpect(view().name("ticket/detail"))
                .andExpect(model().attributeExists("ticket", "comments", "allowedTransitions"))
                .andExpect(model().attribute("isInternalUser", equalTo(true)));
    }

    @Test
    void shouldIncludeTimelineInModelWhenInternalUserViewsTicket() throws Exception {
        mockMvc.perform(get("/tickets/{id}", ticket1.getId())
                        .with(user(new TicketFlowUserDetails(agent))))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("timeline", "internalUsers"));
    }

    @Test
    void shouldNotIncludeTimelineInModelWhenCustomerViewsTicket() throws Exception {
        mockMvc.perform(get("/tickets/{id}", ticket1.getId())
                        .with(user(new TicketFlowUserDetails(customer))))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("timeline", "internalUsers"));
    }

    @Test
    void shouldReturn404WhenTicketDoesNotExist() throws Exception {
        mockMvc.perform(get("/tickets/{id}", 999L)
                        .with(user(new TicketFlowUserDetails(admin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnTicketCreateFormWhenGetRequestIsMade() throws Exception {
        mockMvc.perform(get("/tickets/new")
                        .with(user(new TicketFlowUserDetails(customer))))
                .andExpect(status().isOk())
                .andExpect(view().name("ticket/create"))
                .andExpect(model().attributeExists("ticket"));
    }

    @Test
    void shouldRedirectToTicketDetailWhenTicketIsCreatedSuccessfully() throws Exception {
        mockMvc.perform(post("/tickets")
                        .with(csrf())
                        .with(user(new TicketFlowUserDetails(customer)))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", "Printer not working")
                        .param("description", "Office printer is offline"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/tickets/*"));
    }

    @Test
    void shouldRedirectBackToFormWhenTicketCreationValidationFails() throws Exception {
        mockMvc.perform(post("/tickets")
                        .with(csrf())
                        .with(user(new TicketFlowUserDetails(customer)))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", "")
                        .param("description", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/new"));
    }

    @Test
    void shouldRedirectToTicketDetailWhenAssignIsSuccessful() throws Exception {
        mockMvc.perform(post("/tickets/{id}/assign", ticket2.getId())
                        .with(csrf())
                        .with(user(new TicketFlowUserDetails(admin)))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("assigneeId", String.valueOf(agent.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/" + ticket2.getId()));
    }

    @Test
    void shouldRedirectToTicketDetailWithErrorsWhenAssignFailsWithValidation() throws Exception {
        int invalidAssigneeId = -99;

        mockMvc.perform(post("/tickets/{id}/assign", ticket2.getId())
                        .with(csrf())
                        .with(user(new TicketFlowUserDetails(admin)))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("assigneeId", String.valueOf(invalidAssigneeId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errors"))
                .andExpect(redirectedUrl("/tickets/" + ticket2.getId()));
    }

    @Test
    void shouldRedirectToTicketDetailWhenStartProgressIsSuccessful() throws Exception {
        mockMvc.perform(post("/tickets/{id}/start", ticket2.getId())
                        .with(csrf())
                        .with(user(new TicketFlowUserDetails(agent))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/" + ticket2.getId()));
    }

    @Test
    void shouldRedirectToTicketDetailWhenResolveIsSuccessful() throws Exception {
        mockMvc.perform(post("/tickets/{id}/resolve", ticket1.getId())
                        .with(csrf())
                        .with(user(new TicketFlowUserDetails(agent))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/" + ticket1.getId()));
    }

    @Test
    void shouldRedirectToTicketWhenCommentIsCreatedSuccessfully() throws Exception {
        mockMvc.perform(post("/tickets/{ticketId}/comments", ticket1.getId())
                        .with(csrf())
                        .with(user(new TicketFlowUserDetails(customer)))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("text", "Still having this issue"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/" + ticket1.getId()));
    }

    @Test
    void shouldRedirectToTicketWithoutCreatingCommentWhenValidationFails() throws Exception {
        mockMvc.perform(post("/tickets/{ticketId}/comments", ticket1.getId())
                        .with(csrf())
                        .with(user(new TicketFlowUserDetails(customer)))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("text", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errors"))
                .andExpect(redirectedUrl("/tickets/" + ticket1.getId()));
    }

    @Test
    void shouldRedirectToTicketWhenCommentIsDeletedSuccessfully() throws Exception {
        mockMvc.perform(post("/tickets/{ticketId}/comments/{commentId}/delete",
                        ticket1.getId(), 2L)
                        .with(csrf())
                        .with(user(new TicketFlowUserDetails(customer))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/tickets/" + ticket1.getId()));
    }
}
