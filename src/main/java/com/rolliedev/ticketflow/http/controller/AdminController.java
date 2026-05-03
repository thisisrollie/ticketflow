package com.rolliedev.ticketflow.http.controller;

import com.rolliedev.ticketflow.dto.InternalUserCreateRequest;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasAuthority('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @GetMapping("/new")
    public String createForm(Model model,
                             @ModelAttribute("user") InternalUserCreateRequest user) {
        model.addAttribute("user", user);
        model.addAttribute("roles", List.of(Role.ADMIN, Role.AGENT));
        return "admin/user-create";
    }

    @PostMapping
    public String create(@ModelAttribute @Validated InternalUserCreateRequest user,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("user", user);
            redirectAttributes.addFlashAttribute("errors", bindingResult.getAllErrors());
            return "redirect:/admin/users/new";
        }

        userService.createInternalUser(user);
        return "redirect:/users";
    }
}
