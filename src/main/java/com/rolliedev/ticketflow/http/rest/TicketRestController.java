package com.rolliedev.ticketflow.http.rest;

import com.rolliedev.ticketflow.dto.ActorCommand;
import com.rolliedev.ticketflow.dto.AssignTicketRequest;
import com.rolliedev.ticketflow.dto.ChangePriorityRequest;
import com.rolliedev.ticketflow.dto.CreateTicketRequest;
import com.rolliedev.ticketflow.dto.PageResponse;
import com.rolliedev.ticketflow.dto.TicketEventResponse;
import com.rolliedev.ticketflow.dto.TicketResponse;
import com.rolliedev.ticketflow.dto.TicketSearchFilter;
import com.rolliedev.ticketflow.exception.ResourceNotFoundException;
import com.rolliedev.ticketflow.service.TicketEventService;
import com.rolliedev.ticketflow.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketRestController {

    private final TicketService ticketService;
    private final TicketEventService eventService;

    @GetMapping("/{id}")
    public TicketResponse findById(@PathVariable Long id) {
        return ticketService.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.ticket(id));
    }

    @GetMapping
    public PageResponse<TicketResponse> findAll(TicketSearchFilter filter,
                                                @PageableDefault Pageable pageable) {
        Page<TicketResponse> page = ticketService.findAll(filter, pageable);
        return PageResponse.of(page);
    }

    @GetMapping("/{id}/events")
    public PageResponse<TicketEventResponse> getTimeline(@PathVariable Long id,
                                                         @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TicketEventResponse> page = eventService.getTimeline(id, pageable);
        return PageResponse.of(page);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(@Validated @RequestBody CreateTicketRequest ticket) {
        return ticketService.create(ticket);
    }

    @PatchMapping("/{id}/assign")
    public TicketResponse assign(@PathVariable Long id,
                                 @Validated @RequestBody AssignTicketRequest request) {
        return ticketService.assign(id, request.getActorId(), request.getAssigneeId());
    }

    @PatchMapping("/{id}/start")
    public TicketResponse startProgress(@PathVariable Long id,
                                        @Validated @RequestBody ActorCommand cmd) {
        return ticketService.startProgress(id, cmd.getActorId());
    }

    @PatchMapping("/{id}/request-info")
    public TicketResponse requestCustomerInfo(@PathVariable Long id,
                                              @Validated @RequestBody ActorCommand cmd) {
        return ticketService.requestCustomerInfo(id, cmd.getActorId());
    }

    @PatchMapping("/{id}/resolve")
    public TicketResponse resolve(@PathVariable Long id,
                                  @Validated @RequestBody ActorCommand cmd) {
        return ticketService.resolve(id, cmd.getActorId());
    }

    @PatchMapping("/{id}/close")
    public TicketResponse closeByCustomer(@PathVariable Long id,
                                          @Validated @RequestBody ActorCommand cmd) {
        return ticketService.closeByCustomer(id, cmd.getActorId());
    }

    @PatchMapping("/{id}/priority")
    public TicketResponse changePriority(@PathVariable Long id,
                                         @Validated @RequestBody ChangePriorityRequest request) {
        return ticketService.changePriority(id, request.getActorId(), request.getNewPriority());
    }
}
