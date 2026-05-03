package com.rolliedev.ticketflow.unit.service;

import com.rolliedev.ticketflow.dto.InternalUserCreateRequest;
import com.rolliedev.ticketflow.dto.PublicRegistrationRequest;
import com.rolliedev.ticketflow.dto.UserResponse;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.exception.BusinessRuleViolationException;
import com.rolliedev.ticketflow.exception.InvalidRequestException;
import com.rolliedev.ticketflow.mapper.InternalUserCreateRequestMapper;
import com.rolliedev.ticketflow.mapper.PublicRegistrationRequestMapper;
import com.rolliedev.ticketflow.mapper.UserResponseMapper;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserResponseMapper userResponseMapper;
    @Mock
    private PublicRegistrationRequestMapper publicRegistrationRequestMapper;
    @Mock
    private InternalUserCreateRequestMapper internalUserCreateRequestMapper;
    @InjectMocks
    private UserService userService;

    @Test
    void shouldReturnMappedPageOfUsers() {
        UserEntity userEntity1 = mockUserEntity(1, "clark.kent@gmail.com", Role.CUSTOMER);
        UserEntity userEntity2 = mockUserEntity(2, "lana.lang@gmail.com", Role.AGENT);
        UserResponse response1 = mockUserResponse(1, "clark.kent@gmail.com");
        UserResponse response2 = mockUserResponse(2, "lana.lang@gmail.com");
        PageRequest pageable = PageRequest.of(0, 10);

        doReturn(new PageImpl<>(List.of(userEntity1, userEntity2), pageable, 2))
                .when(userRepository).findAll(pageable);
        doReturn(response1).when(userResponseMapper).map(userEntity1);
        doReturn(response2).when(userResponseMapper).map(userEntity2);

        Page<UserResponse> actualResult = userService.findAll(pageable);

        assertThat(actualResult.getContent()).containsExactly(response1, response2);
        verify(userRepository).findAll(pageable);
        verify(userResponseMapper, times(2)).map(any(UserEntity.class));
    }

    @Test
    void shouldReturnEmptyPageWhenNoUsersFound() {
        PageRequest pageable = PageRequest.of(0, 10);

        doReturn(new PageImpl<>(Collections.emptyList(), pageable, 0))
                .when(userRepository).findAll(pageable);

        Page<UserResponse> actualResult = userService.findAll(pageable);

        assertThat(actualResult.getContent()).isEmpty();
        assertThat(actualResult.getTotalElements()).isZero();
        verify(userRepository).findAll(pageable);
        verify(userResponseMapper, never()).map(any(UserEntity.class));
    }

    @Test
    void shouldFindAllUsersByRole() {
        UserEntity userEntity = mockUserEntity(1, "lana.lang@gmail.com", Role.AGENT);
        UserResponse userResponse = mockUserResponse(1, "lana.lang@gmail.com");

        doReturn(List.of(userEntity)).when(userRepository).findAllByRoleIn(List.of(Role.AGENT));
        doReturn(userResponse).when(userResponseMapper).map(userEntity);

        List<UserResponse> actualResult = userService.findAllByRoleIn(Role.AGENT);

        assertThat(actualResult).containsExactly(userResponse);
        verify(userRepository).findAllByRoleIn(List.of(Role.AGENT));
        verify(userResponseMapper).map(userEntity);
    }

    @Test
    void shouldReturnEmptyListWhenNoUsersFoundByRole() {
        doReturn(Collections.emptyList()).when(userRepository).findAllByRoleIn(List.of(Role.ADMIN, Role.AGENT));

        List<UserResponse> actualResult = userService.findAllByRoleIn(Role.ADMIN, Role.AGENT);

        assertThat(actualResult).isEmpty();
        verify(userRepository).findAllByRoleIn(List.of(Role.ADMIN, Role.AGENT));
        verify(userResponseMapper, never()).map(any(UserEntity.class));
    }

    @Test
    void shouldFindUserSuccessfullyWhenCurrentUserIsAdmin() {
        UserEntity userEntity = mockUserEntity(1, "clark.kent@gmail.com", Role.CUSTOMER);
        UserResponse response = mockUserResponse(1, "clark.kent@gmail.com");
        TicketFlowUserDetails adminUser = mockCurrentUser(99, Role.ADMIN);

        doReturn(Optional.of(userEntity)).when(userRepository).findById(1);
        doReturn(response).when(userResponseMapper).map(userEntity);

        Optional<UserResponse> actualResult = userService.findById(1, adminUser);

        assertThat(actualResult).contains(response);
    }

    @Test
    void shouldFindYourselfSuccessfullyWhenCurrentUserIsCustomer() {
        UserEntity userEntity = mockUserEntity(1, "clark.kent@gmail.com", Role.CUSTOMER);
        UserResponse response = mockUserResponse(1, "clark.kent@gmail.com");
        TicketFlowUserDetails customerUser = mockCurrentUser(1, Role.CUSTOMER);

        doReturn(Optional.of(userEntity)).when(userRepository).findById(1);
        doReturn(response).when(userResponseMapper).map(userEntity);

        Optional<UserResponse> actualResult = userService.findById(1, customerUser);

        assertThat(actualResult).contains(response);
    }

    @Test
    void shouldReturnEmptyOptionalWhenAccessingOtherUserAsCustomer() {
        UserEntity userEntity = mockUserEntity(99, "oliver.queen@gmail.com", Role.CUSTOMER);
        TicketFlowUserDetails customerUser = mockCurrentUser(1, Role.CUSTOMER);

        doReturn(Optional.of(userEntity)).when(userRepository).findById(99);

        Optional<UserResponse> actualResult = userService.findById(99, customerUser);

        assertThat(actualResult).isEmpty();
        verify(userResponseMapper, never()).map(userEntity);
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        TicketFlowUserDetails adminUser = mockCurrentUser(1, Role.ADMIN);

        doReturn(Optional.empty()).when(userRepository).findById(999);

        Optional<UserResponse> actualResult = userService.findById(999, adminUser);

        assertThat(actualResult).isEmpty();
    }

    @Test
    void shouldCreateCustomerSuccessfully() {
        PublicRegistrationRequest registrationRequest = new PublicRegistrationRequest("Clark", "Kent", "clark.kent@gmail.com", "123");
        UserEntity userEntity = mockUserEntity(1, "clark.kent@gmail.com", Role.CUSTOMER);
        UserResponse userResponse = mockUserResponse(1, "clark.kent@gmail.com");

        doReturn(false).when(userRepository).existsByEmail("clark.kent@gmail.com");
        doReturn(userEntity).when(publicRegistrationRequestMapper).map(registrationRequest);
        doReturn(userEntity).when(userRepository).save(userEntity);
        doReturn(userResponse).when(userResponseMapper).map(userEntity);

        UserResponse actualResult = userService.createCustomer(registrationRequest);

        assertThat(actualResult).isEqualTo(userResponse);
        verify(userRepository).save(userEntity);
    }

    @Test
    void shouldThrowBusinessRuleViolationExceptionWhenCreateCustomerWithExistingEmail() {
        PublicRegistrationRequest registrationRequest = new PublicRegistrationRequest("Joe", "Smith", "existing@gmail.com", "pass");

        doReturn(true).when(userRepository).existsByEmail("existing@gmail.com");

        BusinessRuleViolationException actualException = assertThrows(BusinessRuleViolationException.class, () -> userService.createCustomer(registrationRequest));

        assertThat(actualException).hasMessageContaining("existing@gmail.com");
        verify(userRepository, never()).save(any(UserEntity.class));
        verifyNoInteractions(publicRegistrationRequestMapper, userResponseMapper);
    }

    @Test
    void shouldCreateInternalUserWithAdminRoleSuccessfully() {
        InternalUserCreateRequest request = new InternalUserCreateRequest("admin", "admin", "admin@gmail.com", "123", Role.ADMIN);
        UserEntity userEntity = mockUserEntity(1, "admin@gmail.com", Role.ADMIN);
        UserResponse userResponse = mockUserResponse(1, "admin@gmail.com");

        doReturn(false).when(userRepository).existsByEmail("admin@gmail.com");
        doReturn(userEntity).when(internalUserCreateRequestMapper).map(request);
        doReturn(userEntity).when(userRepository).save(userEntity);
        doReturn(userResponse).when(userResponseMapper).map(userEntity);

        UserResponse actualResult = userService.createInternalUser(request);

        assertThat(actualResult).isEqualTo(userResponse);
        verify(userRepository).save(userEntity);
    }

    @Test
    void shouldCreateInternalUserWithAgentRoleSuccessfully() {
        InternalUserCreateRequest request = new InternalUserCreateRequest("agent", "agent", "agent@gmail.com", "123", Role.AGENT);
        UserEntity userEntity = mockUserEntity(2, "agent@gmail.com", Role.AGENT);
        UserResponse userResponse = mockUserResponse(2, "agent@gmail.com");

        doReturn(false).when(userRepository).existsByEmail("agent@gmail.com");
        doReturn(userEntity).when(internalUserCreateRequestMapper).map(request);
        doReturn(userEntity).when(userRepository).save(userEntity);
        doReturn(userResponse).when(userResponseMapper).map(userEntity);

        UserResponse actualResult = userService.createInternalUser(request);

        assertThat(actualResult).isEqualTo(userResponse);
        verify(userRepository).save(userEntity);
    }

    @Test
    void shouldThrowInvalidRequestExceptionWhenCreateInternalUserWithCustomerRole() {
        InternalUserCreateRequest request = new InternalUserCreateRequest("user", "user", "user@gmail.com", "123", Role.CUSTOMER);

        assertThatThrownBy(() -> userService.createInternalUser(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Only admins and agents");

        verifyNoInteractions(userRepository, internalUserCreateRequestMapper, userResponseMapper);
    }

    @Test
    void shouldThrowBusinessRuleViolationExceptionWhenCreateInternalUserWithExistingEmail() {
        InternalUserCreateRequest registrationRequest = new InternalUserCreateRequest("agent", "agent", "existing@gmail.com", "pass", Role.AGENT);

        doReturn(true).when(userRepository).existsByEmail("existing@gmail.com");

        assertThatThrownBy(() -> userService.createInternalUser(registrationRequest))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("existing@gmail.com");

        verify(userRepository, never()).save(any(UserEntity.class));
        verifyNoInteractions(publicRegistrationRequestMapper, userResponseMapper);
    }

    @Test
    void shouldLoadExistingUserByUsernameSuccessfully() {
        UserEntity existingUser = mockUserEntity(1, "clark.kent@gmail.com", Role.CUSTOMER);

        doReturn(Optional.of(existingUser)).when(userRepository).findByEmail("clark.kent@gmail.com");

        UserDetails actualResult = userService.loadUserByUsername("clark.kent@gmail.com");

        assertThat(actualResult).isInstanceOf(TicketFlowUserDetails.class);
        assertThat(actualResult.getUsername()).isEqualTo(existingUser.getEmail());
    }

    @Test
    void shouldThrowUsernameNotFoundExceptionWhenLoadUserByNonExistentUsername() {
        doReturn(Optional.empty()).when(userRepository).findByEmail("dummy@gmail.com");

        assertThatThrownBy(() -> userService.loadUserByUsername("dummy@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("dummy@gmail.com");
    }

    private UserEntity mockUserEntity(Integer id, String email, Role role) {
        return UserEntity.builder()
                .id(id)
                .email(email)
                .role(role)
                .build();
    }

    private UserResponse mockUserResponse(Integer id, String email) {
        return new UserResponse(id, null, email, null, null);
    }

    private TicketFlowUserDetails mockCurrentUser(Integer id, Role role) {
        return new TicketFlowUserDetails(id, "", "", "", List.of(role));
    }
}