package com.rolliedev.ticketflow.service;

import com.rolliedev.ticketflow.dto.CreateUserRequest;
import com.rolliedev.ticketflow.dto.UserResponse;
import com.rolliedev.ticketflow.entity.UserEntity;
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
    private final UserResponseMapper userMapper;

    public Optional<UserResponse> findUser(Integer userId) {
        return userRepository.findById(userId)
                .map(userMapper::toDto);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest userDto) {
        UserEntity user = UserEntity.builder()
                .fullName(userDto.firstName() + " " + userDto.lastName())
                .email(userDto.email())
                .role(userDto.role())
                .build();

        UserEntity savedUser = userRepository.save(user);
        return userMapper.toDto(savedUser);
    }
}
