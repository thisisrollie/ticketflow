package com.rolliedev.ticketflow.unit.service;

import com.rolliedev.ticketflow.dto.TicketEventResponse;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.TicketEventType;
import com.rolliedev.ticketflow.mapper.TicketEventResponseMapper;
import com.rolliedev.ticketflow.repository.TicketEventRepository;
import com.rolliedev.ticketflow.service.TicketEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketEventServiceTest {

    private static final Long TICKET_ID = 1L;
    private static final Integer USER_ID = 1;

    @Mock
    private TicketEventRepository eventRepository;
    @Mock
    private TicketEventResponseMapper eventMapper;
    @InjectMocks
    private TicketEventService eventService;

    @Test
    void shouldReturnTicketTimelineSuccessfully() {
        List<TicketEventEntity> ticketEvents = List.of(
                TicketEventEntity.builder().id(1L).build(),
                TicketEventEntity.builder().id(2L).build()
        );
        doReturn(ticketEvents).when(eventRepository).findAllByTicketId(eq(TICKET_ID), any(Sort.class));
        ticketEvents.forEach(entity -> {
            TicketEventResponse dto = new TicketEventResponse(entity.getId(), null, entity.getEventType(), entity.getPayload(), entity.getCreatedAt());
            doReturn(dto).when(eventMapper).toDto(entity);
        });

        List<TicketEventResponse> actualResult = eventService.getTimeline(TICKET_ID);

        assertThat(actualResult).hasSize(ticketEvents.size());
        verify(eventRepository).findAllByTicketId(eq(TICKET_ID), any(Sort.class));
        verify(eventMapper, times(ticketEvents.size())).toDto(any());
    }

    @Test
    void shouldReturnEmptyListWhenTicketHasNoEvents() {
        doReturn(Collections.EMPTY_LIST).when(eventRepository).findAllByTicketId(eq(TICKET_ID), any(Sort.class));

        List<TicketEventResponse> actualResult = eventService.getTimeline(TICKET_ID);

        assertThat(actualResult).isEmpty();
        verify(eventRepository).findAllByTicketId(eq(TICKET_ID), any(Sort.class));
        verify(eventMapper, never()).toDto(any(TicketEventEntity.class));
    }

    @Test
    void shouldRecordTicketCreationEventSuccessfully() {
        TicketEntity ticket = TicketEntity.builder()
                .id(TICKET_ID)
                .build();
        UserEntity actor = UserEntity.builder()
                .id(USER_ID)
                .build();

        eventService.recordCreatedEvent(ticket, actor);

        ArgumentCaptor<TicketEventEntity> argumentCaptor = ArgumentCaptor.forClass(TicketEventEntity.class);
        verify(eventRepository).save(argumentCaptor.capture());

        assertThat(argumentCaptor.getValue().getEventType()).isEqualTo(TicketEventType.CREATED);

        Map<String, Object> actualPayload = argumentCaptor.getValue().getPayload();
        assertThat(actualPayload).containsEntry("ticketId", ticket.getId().toString());
        assertThat(actualPayload).containsEntry("createdById", actor.getId().toString());
    }
}