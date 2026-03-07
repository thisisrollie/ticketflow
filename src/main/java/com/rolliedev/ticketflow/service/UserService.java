package com.rolliedev.ticketflow.service;

import com.rolliedev.ticketflow.dto.CreateUserRequest;
import com.rolliedev.ticketflow.dto.UserResponse;
import com.rolliedev.ticketflow.mapper.CreateUserRequestMapper;
import com.rolliedev.ticketflow.mapper.UserResponseMapper;
import com.rolliedev.ticketflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserResponseMapper userResponseMapper;
    private final CreateUserRequestMapper createUserRequestMapper;

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
