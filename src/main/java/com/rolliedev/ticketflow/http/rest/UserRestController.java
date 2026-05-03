package com.rolliedev.ticketflow.http.rest;

import com.rolliedev.ticketflow.dto.PageResponse;
import com.rolliedev.ticketflow.dto.PublicRegistrationRequest;
import com.rolliedev.ticketflow.dto.UserResponse;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserRestController {

    private final UserService userService;

    @GetMapping
    public PageResponse<UserResponse> findAll(@PageableDefault Pageable pageable) {
        return PageResponse.of(userService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Integer id,
                                 @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        return userService.findById(id, currentUser)
                .orElseThrow(() -> ResourceNotFoundException.user(id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Validated @RequestBody PublicRegistrationRequest user) {
        UserResponse createdUser = userService.createCustomer(user);
        URI location = URI.create("/api/v1/users/" + createdUser.id());
        return ResponseEntity.created(location)
                .body(createdUser);
    }
}
