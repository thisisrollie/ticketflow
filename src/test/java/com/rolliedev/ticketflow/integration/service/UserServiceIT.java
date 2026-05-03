package com.rolliedev.ticketflow.integration.service;

import com.rolliedev.ticketflow.dto.InternalUserCreateRequest;
import com.rolliedev.ticketflow.dto.PublicRegistrationRequest;
import com.rolliedev.ticketflow.dto.UserResponse;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.InvalidRequestException;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.service.UserService;
import com.rolliedev.ticketflow.testsupport.base.AbstractSpringBootIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserServiceIT extends AbstractSpringBootIT {

    @Autowired
    private UserService userService;

    private UserEntity admin, agent, customer, secondCustomer;

    @BeforeEach
    void setUp() {
        admin = userRepository.findByEmail("lex.luthor@gmail.com").orElseThrow();
        agent = userRepository.findByEmail("bruce.wayne@gmail.com").orElseThrow();
        customer = userRepository.findByEmail("clark.kent@gmail.com").orElseThrow();
        secondCustomer = userRepository.findByEmail("oliver.queen@gmail.com").orElseThrow();
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void shouldFindAllUsersWhenActorIsAdmin() {
        Page<UserResponse> actualResult = userService.findAll(PageRequest.of(0, 10));

        assertThat(actualResult.getTotalElements()).isEqualTo(4);
        assertThat(actualResult.getContent())
                .extracting(UserResponse::id)
                .containsExactlyInAnyOrder(admin.getId(), agent.getId(), customer.getId(), secondCustomer.getId());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void shouldReturnPagedUsersWhenPageableIsProvided() {
        Page<UserResponse> actualResult = userService.findAll(
                PageRequest.of(0, 2, Sort.Direction.ASC, "id")
        );

        assertThat(actualResult.getTotalElements()).isEqualTo(4);
        assertThat(actualResult.getTotalPages()).isEqualTo(2);
        assertThat(actualResult.getContent()).hasSize(2);
    }

    @Test
    @WithMockUser(authorities = "CUSTOMER")
    void shouldThrowAccessDeniedExceptionWhenCustomerTriesToFindAllUsers() {
        assertThatThrownBy(() -> userService.findAll(PageRequest.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldReturnUsersWithGivenRoleWhenSingleRoleIsProvided() {
        List<UserResponse> actualResult = userService.findAllByRoleIn(Role.ADMIN);

        assertThat(actualResult).hasSize(1);
        assertThat(actualResult.getFirst().id()).isEqualTo(admin.getId());
        assertThat(actualResult.getFirst().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldReturnUsersWithGivenRolesWhenMultipleRolesAreProvided() {
        List<UserResponse> actualResult = userService.findAllByRoleIn(Role.ADMIN, Role.AGENT);

        assertThat(actualResult).hasSize(2);

        assertThat(actualResult)
                .extracting(UserResponse::id)
                .containsExactlyInAnyOrder(admin.getId(), agent.getId());

        assertThat(actualResult)
                .extracting(UserResponse::role)
                .containsExactlyInAnyOrder(Role.ADMIN, Role.AGENT);
    }

    @Test
    void shouldReturnUserWhenActorIsAdmin() {
        TicketFlowUserDetails currentUser = new TicketFlowUserDetails(admin);

        Optional<UserResponse> actualResult = userService.findById(customer.getId(), currentUser);

        assertThat(actualResult).isPresent();
        assertThat(actualResult.get().id()).isEqualTo(customer.getId());
    }

    @Test
    void shouldReturnOwnUserWhenActorIsCustomer() {
        TicketFlowUserDetails currentUser = new TicketFlowUserDetails(customer);

        Optional<UserResponse> actualResult = userService.findById(customer.getId(), currentUser);

        assertThat(actualResult).isPresent();
        assertThat(actualResult.get().id()).isEqualTo(customer.getId());
    }

    @Test
    void shouldReturnEmptyOptionalWhenCustomerTriesToFindAnotherUser() {
        TicketFlowUserDetails currentUser = new TicketFlowUserDetails(customer);

        Optional<UserResponse> actualResult = userService.findById(secondCustomer.getId(), currentUser);

        assertThat(actualResult).isEmpty();
    }

    @Test
    void shouldReturnEmptyOptionalWhenUserDoesNotExist() {
        TicketFlowUserDetails currentUser = new TicketFlowUserDetails(admin);

        Optional<UserResponse> actualResult = userService.findById(999, currentUser);

        assertThat(actualResult).isEmpty();
    }

    @Test
    void shouldCreateCustomerWhenEmailIsUnique() {
        PublicRegistrationRequest request = new PublicRegistrationRequest(
                "Barry", "Allen", "barry.allen@gmail.com", "123"
        );

        UserResponse actualResult = userService.createCustomer(request);
        flushAndClear();

        UserEntity persisted = userRepository.findByEmail("barry.allen@gmail.com").orElseThrow();

        assertThat(actualResult.id()).isEqualTo(persisted.getId());
        assertThat(persisted.getFullName()).isEqualTo("Barry Allen");
        assertThat(persisted.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void shouldThrowBusinessRuleViolationExceptionWhenCreatingUserWithExistingEmail() {
        PublicRegistrationRequest request = new PublicRegistrationRequest(
                "Clark2", "Kent2", customer.getEmail(), "test"
        );

        assertThatThrownBy(() -> userService.createCustomer(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("User with email " + customer.getEmail() + " already exists");
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void shouldCreateInternalUserWithAgentRoleWhenActorIsAdmin() {
        InternalUserCreateRequest request = new InternalUserCreateRequest(
                "Hal", "Jordan", "hal.jordan@gmail.com", "123", Role.AGENT
        );

        UserResponse actualResult = userService.createInternalUser(request);
        flushAndClear();

        UserEntity persisted = userRepository.findByEmail("hal.jordan@gmail.com").orElseThrow();

        assertThat(actualResult.id()).isEqualTo(persisted.getId());
        assertThat(persisted.getFullName()).isEqualTo("Hal Jordan");
        assertThat(persisted.getRole()).isEqualTo(Role.AGENT);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void shouldThrowInvalidRequestExceptionWhenInternalUserRoleIsCustomer() {
        InternalUserCreateRequest request = new InternalUserCreateRequest(
                "test", "test", "test@gmail.com", "123", Role.CUSTOMER
        );

        assertThatThrownBy(() -> userService.createInternalUser(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Only admins and agents can be registered as internal users");
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void shouldThrowBusinessRuleViolationExceptionWhenCreatingInternalUserWithExistingEmail() {
        InternalUserCreateRequest request = new InternalUserCreateRequest(
                "Bruce2", "Wayne2", agent.getEmail(), "test", Role.AGENT
        );

        assertThatThrownBy(() -> userService.createInternalUser(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("User with email " + agent.getEmail() + " already exists");
    }

    @Test
    @WithMockUser(authorities = "CUSTOMER")
    void shouldThrowAccessDeniedExceptionWhenNonAdminTriesToCreateInternalUser() {
        InternalUserCreateRequest request = new InternalUserCreateRequest(
                "test", "test", "test@gmail.com", "test", Role.AGENT
        );

        assertThatThrownBy(() -> userService.createInternalUser(request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldLoadUserByUsernameWhenUserWithSuchEmailExists() {
        UserDetails actualResult = userService.loadUserByUsername(agent.getEmail());

        assertThat(actualResult).isInstanceOf(TicketFlowUserDetails.class);
        assertThat(actualResult.getUsername()).isEqualTo(agent.getEmail());
        assertThat(actualResult.getPassword()).isEqualTo(agent.getPassword());

        assertThat(actualResult.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(Role.AGENT.name());
    }

    @Test
    void shouldThrowUsernameNotFoundExceptionIfUserWithSuchEmailDoesNotExist() {
        String dummyEmail = "dummy@gmail.com";

        assertThatThrownBy(() -> userService.loadUserByUsername(dummyEmail))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Failed to retrieve user: " + dummyEmail);
    }
}
