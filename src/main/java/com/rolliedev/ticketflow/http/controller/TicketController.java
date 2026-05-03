package com.rolliedev.ticketflow.http.controller;

import com.rolliedev.ticketflow.dto.AssignTicketRequest;
import com.rolliedev.ticketflow.dto.ChangePriorityRequest;
import com.rolliedev.ticketflow.dto.CreateTicketRequest;
import com.rolliedev.ticketflow.dto.TicketSearchFilter;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.service.CommentService;
import com.rolliedev.ticketflow.service.TicketEventService;
import com.rolliedev.ticketflow.service.TicketService;
import com.rolliedev.ticketflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final UserService userService;
    private final TicketEventService eventService;
    private final CommentService commentService;

    @GetMapping
    public String findAll(Model model,
                          @ModelAttribute TicketSearchFilter filter,
                          @PageableDefault Pageable pageable,
                          @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        model.addAttribute("page", ticketService.findAll(filter, pageable, currentUser));
        model.addAttribute("filter", filter);
        model.addAttribute("filterQueryParams", filter.toQueryString());
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("priorities", TicketPriority.values());
        if (currentUser.hasAuthority(Role.ADMIN) || currentUser.hasAuthority(Role.AGENT)) {
            model.addAttribute("assignees", userService.findAllByRoleIn(Role.ADMIN, Role.AGENT));
        }

        return "ticket/list";
    }

    @GetMapping("/{id}")
    public String findById(@PathVariable Long id,
                           @AuthenticationPrincipal TicketFlowUserDetails currentUser,
                           Model model) {
        return ticketService.findById(id, currentUser)
                .map(ticket -> {
                    boolean isInternalUser = currentUser.hasAuthority(Role.ADMIN) || currentUser.hasAuthority(Role.AGENT);
                    boolean isAdmin = currentUser.hasAuthority(Role.ADMIN);

                    model.addAttribute("ticket", ticket);
                    model.addAttribute("comments", commentService.findAllBy(ticket.id(), Pageable.unpaged()).getContent());
                    model.addAttribute("allowedTransitions", ticket.status().getAllowedTransitions());

                    model.addAttribute("isInternalUser", isInternalUser);
                    model.addAttribute("isAdmin", isAdmin);
                    model.addAttribute("currentUserId", currentUser.getId());

                    if (isInternalUser) {
                        model.addAttribute("internalUsers", userService.findAllByRoleIn(Role.ADMIN, Role.AGENT));
                        model.addAttribute("timeline", eventService
                                .getTimeline(ticket.id(), Pageable.unpaged(Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                                .getContent()
                        );
                    }

                    return "ticket/detail";
                })
                .orElseThrow(() -> ResourceNotFoundException.ticket(id));
    }

    @GetMapping("/new")
    public String createForm(Model model,
                             @ModelAttribute("ticket") CreateTicketRequest ticket) {
        model.addAttribute("ticket", ticket);
        return "ticket/create";
    }

    @PostMapping
    public String create(@ModelAttribute("ticket") @Validated CreateTicketRequest ticket,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("ticket", ticket);
            redirectAttributes.addFlashAttribute("errors", bindingResult.getAllErrors());
            return "redirect:/tickets/new";
        }
        return "redirect:/tickets/" + ticketService.create(ticket, currentUser.getId()).id();
    }

    @PostMapping("/{id}/assign")
    public String assign(@PathVariable Long id,
                         @AuthenticationPrincipal TicketFlowUserDetails currentUser,
                         @ModelAttribute @Validated AssignTicketRequest request,
                         BindingResult bindingResult,
                         RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("errors", bindingResult.getAllErrors());
        } else {
            ticketService.assign(id, currentUser.getId(), request.getAssigneeId());
        }
        return "redirect:/tickets/{id}";
    }

    @PostMapping("/{id}/start")
    public String startProgress(@PathVariable Long id,
                                @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        ticketService.startProgress(id, currentUser.getId());
        return "redirect:/tickets/{id}";
    }

    @PostMapping("/{id}/request-info")
    public String requestCustomerInfo(@PathVariable Long id,
                                      @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        ticketService.requestCustomerInfo(id, currentUser.getId());
        return "redirect:/tickets/{id}";
    }

    @PostMapping("/{id}/resolve")
    public String resolve(@PathVariable Long id,
                          @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        ticketService.resolve(id, currentUser.getId());
        return "redirect:/tickets/{id}";
    }

    @PostMapping("/{id}/close")
    public String closeByCustomer(@PathVariable Long id,
                                  @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        ticketService.closeByCustomer(id, currentUser.getId());
        return "redirect:/tickets/{id}";
    }

    @PostMapping("/{id}/priority")
    public String changePriority(@PathVariable Long id,
                                 @AuthenticationPrincipal TicketFlowUserDetails currentUser,
                                 @ModelAttribute @Validated ChangePriorityRequest request,
                                 BindingResult bindingResult,
                                 RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("errors", bindingResult.getAllErrors());
        } else {
            ticketService.changePriority(id, currentUser.getId(), request.getNewPriority());
        }
        return "redirect:/tickets/{id}";
    }
}
