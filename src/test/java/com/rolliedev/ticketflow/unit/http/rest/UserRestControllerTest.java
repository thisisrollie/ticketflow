package com.rolliedev.ticketflow.unit.http.rest;

import com.rolliedev.ticketflow.config.SecurityConfiguration;
import com.rolliedev.ticketflow.dto.PublicRegistrationRequest;
import com.rolliedev.ticketflow.dto.UserResponse;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.http.rest.UserRestController;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserRestController.class)
@Import(SecurityConfiguration.class)
class UserRestControllerTest {

    private static final Integer ADMIN_ID = 1;
    private static final Integer USER_ID = 5;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private TicketFlowUserDetails adminDetails;

    @BeforeEach
    void setUp() {
        adminDetails = mockUserDetails(ADMIN_ID, Role.ADMIN);
    }

    @Test
    void shouldReturnPageOfUsersWhenUsersExist() throws Exception {
        UserResponse user1 = mockUserResponse(1);
        UserResponse user2 = mockUserResponse(2);
        PageImpl<UserResponse> page = new PageImpl<>(List.of(user1, user2), PageRequest.of(0, 10), 2);

        doReturn(page).when(userService).findAll(any(Pageable.class));

        mockMvc.perform(get("/api/v1/users")
                        .with(user(adminDetails)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.content").isArray(),
                        jsonPath("$.content.length()").value(2),
                        jsonPath("$.metadata.totalElements").value(2)
                );
    }

    @Test
    void shouldReturnEmptyPageWhenNoUsersExist() throws Exception {
        PageImpl<UserResponse> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

        doReturn(emptyPage).when(userService).findAll(any(Pageable.class));

        mockMvc.perform(get("/api/v1/users")
                        .with(user(adminDetails)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.content").isEmpty(),
                        jsonPath("$.metadata.totalElements").value(0)
                );
    }

    @Test
    void shouldReturnUserWhenUserExists() throws Exception {
        UserResponse user = mockUserResponse(USER_ID);

        doReturn(Optional.of(user)).when(userService).findById(eq(USER_ID), any());

        mockMvc.perform(get("/api/v1/users/{id}", USER_ID)
                        .with(user(adminDetails)))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$.id").value(USER_ID)
                );
    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        doReturn(Optional.empty()).when(userService).findById(eq(USER_ID), any());

        mockMvc.perform(get("/api/v1/users/{id}", USER_ID)
                        .with(user(adminDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateCustomerAndReturnLocationHeaderWhenRequestIsValid() throws Exception {
        PublicRegistrationRequest request = new PublicRegistrationRequest("Lana", "Lang", "lana.lang@gmail.com", "123");
        UserResponse createdUser = mockUserResponse(USER_ID);

        doReturn(createdUser).when(userService).createCustomer(request);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpectAll(
                        status().isCreated(),
                        header().string("Location", "/api/v1/users/" + USER_ID),
                        jsonPath("$.id").value(USER_ID)
                );
    }

    @Test
    void shouldReturnBadRequestWhenPublicRegistrationRequestIsInvalid() throws Exception {
        PublicRegistrationRequest invalidRequest = new PublicRegistrationRequest(null, null, null, null);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    private TicketFlowUserDetails mockUserDetails(Integer id, Role role) {
        UserEntity user = UserEntity.builder()
                .id(id)
                .email(role.name().toLowerCase() + "@test.com")
                .role(role)
                .build();
        return new TicketFlowUserDetails(user);
    }

    private UserResponse mockUserResponse(Integer id) {
        return mock(UserResponse.class, invocation ->
                invocation.getMethod().getName().equals("id") ? id : null);
    }
}