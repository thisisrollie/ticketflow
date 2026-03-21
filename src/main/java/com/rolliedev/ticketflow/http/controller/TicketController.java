package com.rolliedev.ticketflow.http.controller;

import com.rolliedev.ticketflow.dto.ActorCommand;
import com.rolliedev.ticketflow.dto.AssignTicketRequest;
import com.rolliedev.ticketflow.dto.ChangePriorityRequest;
import com.rolliedev.ticketflow.dto.CreateTicketRequest;
import com.rolliedev.ticketflow.dto.TicketResponse;
import com.rolliedev.ticketflow.dto.TicketSearchFilter;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.service.CommentService;
import com.rolliedev.ticketflow.service.TicketEventService;
import com.rolliedev.ticketflow.service.TicketService;
import com.rolliedev.ticketflow.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
                          @PageableDefault Pageable pageable) {
        Page<TicketResponse> page = ticketService.findAll(filter, pageable);
        model.addAttribute("page", page);
        model.addAttribute("filter", filter);
        model.addAttribute("statuses", TicketStatus.values());
        model.addAttribute("priorities", TicketPriority.values());
        return "ticket/list";
    }

    @GetMapping("/{id}")
    public String findById(@PathVariable Long id, Model model) {
        return ticketService.findById(id)
                .map(ticket -> {
                    model.addAttribute("ticket", ticket);
                    model.addAttribute("users", userService.findAllByRoleIn(Role.ADMIN, Role.AGENT));
                    model.addAttribute("timeline", eventService.getTimeline(id));
                    model.addAttribute("comments", commentService.findAllBy(id));
                    model.addAttribute("allowedTransitions", ticket.status().getAllowedTransitions());
                    return "ticket/detail";
                })
                .orElseThrow(() -> ResourceNotFoundException.ticket(id));
    }

    @GetMapping("/new")
    public String createForm(Model model, @ModelAttribute("ticket") CreateTicketRequest ticket) {
        model.addAttribute("ticket", ticket);
        return "ticket/create";
    }

    @PostMapping
    public String create(@ModelAttribute("ticket") @Validated CreateTicketRequest ticket,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("ticket", ticket);
            redirectAttributes.addFlashAttribute("errors", bindingResult.getAllErrors());
            return "redirect:/tickets/new";
        }
        return "redirect:/tickets/" + ticketService.create(ticket).id();
    }

    @PostMapping("/{id}/assign")
    public String assign(@PathVariable Long id,
                         @ModelAttribute @Validated AssignTicketRequest request,
                         BindingResult bindingResult,
                         RedirectAttributes ra) {
        executeIfValid(bindingResult, ra, () -> ticketService.assign(id, request.getActorId(), request.getAssigneeId()));
        return "redirect:/tickets/{id}";
    }

    @PostMapping("/{id}/start")
    public String startProgress(@PathVariable Long id,
                                @ModelAttribute @Validated ActorCommand cmd,
                                BindingResult bindingResult,
                                RedirectAttributes ra) {
        executeIfValid(bindingResult, ra, () -> ticketService.startProgress(id, cmd.getActorId()));
        return "redirect:/tickets/{id}";
    }

    @PostMapping("/{id}/request-info")
    public String requestCustomerInfo(@PathVariable Long id,
                                      @ModelAttribute @Validated ActorCommand cmd,
                                      BindingResult bindingResult,
                                      RedirectAttributes ra) {
        executeIfValid(bindingResult, ra, () -> ticketService.requestCustomerInfo(id, cmd.getActorId()));
        return "redirect:/tickets/{id}";
    }

    @PostMapping("/{id}/resolve")
    public String resolve(@PathVariable Long id,
                          @ModelAttribute @Validated ActorCommand cmd,
                          BindingResult bindingResult,
                          RedirectAttributes ra) {
        executeIfValid(bindingResult, ra, () -> ticketService.resolve(id, cmd.getActorId()));
        return "redirect:/tickets/{id}";
    }

    @PostMapping("/{id}/close")
    public String closeByCustomer(@PathVariable Long id,
                                  @ModelAttribute @Validated ActorCommand cmd,
                                  BindingResult bindingResult,
                                  RedirectAttributes ra) {
        executeIfValid(bindingResult, ra, () -> ticketService.closeByCustomer(id, cmd.getActorId()));
        return "redirect:/tickets/{id}";
    }

    @PostMapping("/{id}/priority")
    public String changePriority(@PathVariable Long id,
                                 @ModelAttribute @Validated ChangePriorityRequest request,
                                 BindingResult bindingResult,
                                 RedirectAttributes ra) {
        executeIfValid(bindingResult, ra, () -> ticketService.changePriority(id, request.getActorId(), request.getNewPriority()));
        return "redirect:/tickets/{id}";
    }

    private void executeIfValid(BindingResult bindingResult, RedirectAttributes redirectAttributes, Runnable action) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errors", bindingResult.getAllErrors());
        } else {
            action.run();
        }
    }
}
