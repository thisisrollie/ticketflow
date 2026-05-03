package com.rolliedev.ticketflow.integration.http.rest;

import com.rolliedev.ticketflow.dto.PublicRegistrationRequest;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.testsupport.base.AbstractRestIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class UserRestControllerIT extends AbstractRestIT {

    private UserEntity admin, customer;

    @BeforeEach
    void setUp() {
        admin = userRepository.findByEmail("lex.luthor@gmail.com").orElseThrow();
        customer = userRepository.findByEmail("clark.kent@gmail.com").orElseThrow();
    }

    @Test
    void shouldReturnPagedUsersWhenCalledByAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.metadata.page").value(0),
                        jsonPath("$.metadata.size").value(10),
                        jsonPath("$.metadata.totalElements").value(4),
                        jsonPath("$.content.length()").value(4)
                );
    }

    @Test
    void shouldReturnCorrectPageSizeWhenPageSizeParamIsProvided() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .with(httpBasic("lex.luthor@gmail.com", "123"))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.metadata.totalElements").value(4),
                        jsonPath("$.content.length()").value(2)
                );
    }

    @Test
    void shouldReturn403WhenAgentTriesToListAllUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .with(httpBasic("bruce.wayne@gmail.com", "123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn403WhenCustomerTriesToListAllUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn401WhenListingUsersWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnAnyUserWhenCalledByAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", customer.getId())
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(customer.getId()),
                        jsonPath("$.email").value("clark.kent@gmail.com")
                );
    }

    @Test
    void shouldReturnAnyUserWhenCalledByAgent() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", customer.getId())
                        .with(httpBasic("bruce.wayne@gmail.com", "123")))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(customer.getId())
                );
    }

    @Test
    void shouldReturnOwnProfileWhenCalledByCustomer() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", customer.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(customer.getId()),
                        jsonPath("$.email").value("clark.kent@gmail.com")
                );
    }

    @Test
    void shouldReturn404WhenCustomerRequestsAnotherUsersProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", admin.getId())
                        .with(httpBasic("clark.kent@gmail.com", "123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404WhenRequestedUserDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", 999)
                        .with(httpBasic("lex.luthor@gmail.com", "123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WhenFetchingUserProfileWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", customer.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreateCustomerAndReturn201WithLocationHeaderWhenRequestIsValid() throws Exception {
        PublicRegistrationRequest request = new PublicRegistrationRequest(
                "Hal", "Jordan", "hal.jordan@gmail.com", "securePass123"
        );

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isCreated(),
                        header().string("Location", matchesPattern("/api/v1/users/\\d+")),
                        jsonPath("$.email").value("hal.jordan@gmail.com"),
                        jsonPath("$.fullName").value("Hal Jordan")
                );
    }

    @Test
    void shouldAllowUnauthenticatedAccessWhenCreatingCustomer() throws Exception {
        PublicRegistrationRequest request = new PublicRegistrationRequest(
                "Hal", "Jordan", "hal.jordan@gmail.com", "securePass123"
        );

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldReturn409WhenEmailAlreadyExists() throws Exception {
        PublicRegistrationRequest request = new PublicRegistrationRequest(
                "Clark", "Kent", "clark.kent@gmail.com", "somePassword"
        );

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn400WhenEmailIsMissingInRequest() throws Exception {
        PublicRegistrationRequest request = new PublicRegistrationRequest(
                "Hal", "Jordan", null, "securePass123"
        );

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenEmailIsInInvalidFormat() throws Exception {
        PublicRegistrationRequest request = new PublicRegistrationRequest(
                "Hal", "Jordan", "not-an-email", "securePass123"
        );

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenPasswordIsMissingInRequest() throws Exception {
        PublicRegistrationRequest request = new PublicRegistrationRequest(
                "Hal", "Jordan", "hal.jordan@gmail.com", null
        );

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenRequestBodyIsAbsent() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
