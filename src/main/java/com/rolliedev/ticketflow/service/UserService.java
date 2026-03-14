package com.rolliedev.ticketflow.service;

import com.rolliedev.ticketflow.dto.CreateUserRequest;
import com.rolliedev.ticketflow.dto.UserResponse;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.mapper.CreateUserRequestMapper;
import com.rolliedev.ticketflow.mapper.UserResponseMapper;
import com.rolliedev.ticketflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserResponseMapper userResponseMapper;
    private final CreateUserRequestMapper createUserRequestMapper;

    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
                .map(userResponseMapper::map)
                .toList();
    }

    public List<UserResponse> findAllByRole(Role role) {
        return findAllByRoleIn(role);
    }

    public List<UserResponse> findAllByRoleIn(Role... roles) {
        return userRepository.findAllByRoleIn(Arrays.asList(roles)).stream()
                .map(userResponseMapper::map)
                .toList();
    }

    public Optional<UserResponse> findById(Integer id) {
        return userRepository.findById(id)
                .map(userResponseMapper::map);
    }

    @Transactional
    public UserResponse create(CreateUserRequest userDto) {
        return Optional.of(userDto)
                .map(createUserRequestMapper::map)
                .map(userRepository::save)
                .map(userResponseMapper::map)
                .orElseThrow();
    }
}
