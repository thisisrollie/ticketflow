package com.rolliedev.ticketflow.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserResponseMapper userResponseMapper;
    private final PublicRegistrationRequestMapper publicRegistrationRequestMapper;
    private final InternalUserCreateRequestMapper internalUserCreateRequestMapper;

    @PreAuthorize("hasAuthority('ADMIN')")
    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(userResponseMapper::map);
    }

    public List<UserResponse> findAllByRoleIn(Role... roles) {
        return userRepository.findAllByRoleIn(Arrays.asList(roles)).stream()
                .map(userResponseMapper::map)
                .toList();
    }

    public Optional<UserResponse> findById(Integer id, TicketFlowUserDetails actor) {
        Optional<UserEntity> maybeUser = userRepository.findById(id);
        if (actor.hasAuthority(Role.CUSTOMER)) {
            maybeUser = maybeUser.filter(u -> u.getId().equals(actor.getId()));
        }
        return maybeUser.map(userResponseMapper::map);
    }

    @Transactional
    public UserResponse createCustomer(PublicRegistrationRequest userDto) {
        ensureEmailIsUnique(userDto.email());
        UserEntity userEntity = publicRegistrationRequestMapper.map(userDto);
        return userResponseMapper.map(userRepository.save(userEntity));
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMIN')")
    public UserResponse createInternalUser(InternalUserCreateRequest userDto) {
        if (userDto.role() != Role.ADMIN && userDto.role() != Role.AGENT) {
            throw new InvalidRequestException("Only admins and agents can be registered as internal users");
        }

        ensureEmailIsUnique(userDto.email());
        UserEntity userEntity = internalUserCreateRequestMapper.map(userDto);
        return userResponseMapper.map(userRepository.save(userEntity));
    }

    private void ensureEmailIsUnique(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessRuleViolationException("User with email " + email + " already exists");
        }
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
                .map(TicketFlowUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Failed to retrieve user: " + username));
    }
}
