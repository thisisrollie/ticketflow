package com.rolliedev.ticketflow.unit.service;

import com.rolliedev.ticketflow.dto.CreateUserRequest;
import com.rolliedev.ticketflow.dto.UserResponse;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.mapper.UserResponseMapper;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Integer USER_ID = 1;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserResponseMapper userMapper;
    @InjectMocks
    private UserService userService;

    @Test
    void shouldFindUserSuccessfully() {
        UserEntity user = UserEntity.builder()
                .id(USER_ID)
                .build();
        doReturn(Optional.of(user)).when(userRepository).findById(USER_ID);
        UserResponse userDto = new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getCreatedAt());
        doReturn(userDto).when(userMapper).toDto(user);

        Optional<UserResponse> actualResult = userService.findUser(USER_ID);

        assertThat(actualResult).isPresent();
        assertThat(actualResult.get().id()).isEqualTo(user.getId());
        verify(userRepository).findById(USER_ID);
        verify(userMapper).toDto(user);
    }

    @Test
    void shouldReturnEmptyOptionalWhenUserNotFound() {
        doReturn(Optional.empty()).when(userRepository).findById(USER_ID);

        Optional<UserResponse> actualResult = userService.findUser(USER_ID);

        assertThat(actualResult).isEmpty();
        verify(userRepository).findById(USER_ID);
        verify(userMapper, never()).toDto(any(UserEntity.class));
    }

    @Test
    void shouldCreateUserSuccessfully() {
        CreateUserRequest createRequest = new CreateUserRequest("Clark", "Kent", "test@gmail.com", Role.CUSTOMER);

        ArgumentCaptor<UserEntity> argumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
        UserEntity userEntity = UserEntity.builder().id(USER_ID).build();
        doReturn(userEntity).when(userRepository).save(argumentCaptor.capture());
        UserResponse userDto = new UserResponse(userEntity.getId(), userEntity.getFullName(), userEntity.getEmail(), userEntity.getRole(), userEntity.getCreatedAt());
        doReturn(userDto).when(userMapper).toDto(userEntity);

        UserResponse actualResult = userService.createUser(createRequest);

        assertThat(actualResult).isNotNull();
        assertThat(actualResult).isEqualTo(userDto);
        assertThat(argumentCaptor.getValue().getFullName()).isEqualTo(createRequest.firstName() + " " + createRequest.lastName());

        verify(userRepository).save(any(UserEntity.class));
        verify(userMapper).toDto(userEntity);
    }
}