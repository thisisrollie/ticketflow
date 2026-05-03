package com.rolliedev.ticketflow.http.controller;

import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public String findAll(Model model,
                          @PageableDefault(sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        model.addAttribute("page", userService.findAll(pageable));
        model.addAttribute("filterQueryParams", "");
        return "user/list";
    }

    @GetMapping("/{id}")
    public String findById(@PathVariable Integer id,
                           @AuthenticationPrincipal TicketFlowUserDetails currentUser,
                           Model model) {
        return userService.findById(id, currentUser)
                .map(user -> {
                    model.addAttribute("user", user);
                    return "user/detail";
                })
                .orElseThrow(() -> ResourceNotFoundException.user(id));
    }
}
