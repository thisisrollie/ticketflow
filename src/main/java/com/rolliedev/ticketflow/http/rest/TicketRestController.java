package com.rolliedev.ticketflow.http.rest;

import com.rolliedev.ticketflow.dto.AssignTicketRequest;
import com.rolliedev.ticketflow.dto.ChangePriorityRequest;
import com.rolliedev.ticketflow.dto.CreateTicketRequest;
import com.rolliedev.ticketflow.dto.PageResponse;
import com.rolliedev.ticketflow.dto.TicketEventResponse;
import com.rolliedev.ticketflow.dto.TicketResponse;
import com.rolliedev.ticketflow.dto.TicketSearchFilter;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.security.TicketFlowUserDetails;
import com.rolliedev.ticketflow.service.TicketEventService;
import com.rolliedev.ticketflow.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketRestController {

    private final TicketService ticketService;
    private final TicketEventService eventService;

    @GetMapping("/{id}")
    public TicketResponse findById(@PathVariable Long id,
                                   @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        return ticketService.findById(id, currentUser)
                .orElseThrow(() -> ResourceNotFoundException.ticket(id));
    }

    @GetMapping
    public PageResponse<TicketResponse> findAll(TicketSearchFilter filter,
                                                @PageableDefault Pageable pageable,
                                                @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        return PageResponse.of(ticketService.findAll(filter, pageable, currentUser));
    }

    @GetMapping("/{id}/events")
    public PageResponse<TicketEventResponse> getTimeline(@PathVariable Long id,
                                                         @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
                                                         @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        ticketService.findById(id, currentUser)
                .orElseThrow(() -> ResourceNotFoundException.ticket(id));

        return PageResponse.of(eventService.getTimeline(id, pageable));
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Validated @RequestBody CreateTicketRequest ticket,
                                                 @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        TicketResponse createdTicket = ticketService.create(ticket, currentUser.getId());
        URI location = URI.create("/api/v1/tickets/" + createdTicket.id());
        return ResponseEntity.created(location)
                .body(createdTicket);
    }

    @PatchMapping("/{id}/assign")
    public TicketResponse assign(@PathVariable Long id,
                                 @Validated @RequestBody AssignTicketRequest request,
                                 @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        return ticketService.assign(id, currentUser.getId(), request.getAssigneeId());
    }

    @PatchMapping("/{id}/start")
    public TicketResponse startProgress(@PathVariable Long id,
                                        @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        return ticketService.startProgress(id, currentUser.getId());
    }

    @PatchMapping("/{id}/request-info")
    public TicketResponse requestCustomerInfo(@PathVariable Long id,
                                              @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        return ticketService.requestCustomerInfo(id, currentUser.getId());
    }

    @PatchMapping("/{id}/resolve")
    public TicketResponse resolve(@PathVariable Long id,
                                  @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        return ticketService.resolve(id, currentUser.getId());
    }

    @PatchMapping("/{id}/close")
    public TicketResponse closeByCustomer(@PathVariable Long id,
                                          @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        return ticketService.closeByCustomer(id, currentUser.getId());
    }

    @PatchMapping("/{id}/priority")
    public TicketResponse changePriority(@PathVariable Long id,
                                         @Validated @RequestBody ChangePriorityRequest request,
                                         @AuthenticationPrincipal TicketFlowUserDetails currentUser) {
        return ticketService.changePriority(id, currentUser.getId(), request.getNewPriority());
    }
}
