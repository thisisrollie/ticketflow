package com.rolliedev.ticketflow.unit.service;

import com.rolliedev.ticketflow.dto.TicketEventResponse;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.TicketEventEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.TicketEventType;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.mapper.TicketEventResponseMapper;
import com.rolliedev.ticketflow.repository.TicketEventRepository;
import com.rolliedev.ticketflow.service.TicketEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketEventServiceTest {

    private static final Long TICKET_ID = 1L;
    private static final Integer USER_ID = 1;
    private static final Long COMMENT_ID = 10L;

    @Mock
    private TicketEventRepository eventRepository;
    @Mock
    private TicketEventResponseMapper eventMapper;
    @InjectMocks
    private TicketEventService eventService;

    @Test
    void shouldReturnMappedPageOfTicketEvents() {
        PageRequest pageable = PageRequest.of(0, 10);
        TicketEventEntity event1 = TicketEventEntity.builder().id(1L).build();
        TicketEventEntity event2 = TicketEventEntity.builder().id(2L).build();
        TicketEventResponse eventResponse1 = mock(TicketEventResponse.class);
        TicketEventResponse eventResponse2 = mock(TicketEventResponse.class);

        doReturn(new PageImpl<>(List.of(event1, event2), pageable, 2))
                .when(eventRepository).findAllByTicketId(TICKET_ID, pageable);
        doReturn(eventResponse1).when(eventMapper).map(event1);
        doReturn(eventResponse2).when(eventMapper).map(event2);

        Page<TicketEventResponse> actualResult = eventService.getTimeline(TICKET_ID, pageable);

        assertThat(actualResult.getContent()).containsExactly(eventResponse1, eventResponse2);
        verify(eventRepository).findAllByTicketId(TICKET_ID, pageable);
        verify(eventMapper, times(2)).map(any(TicketEventEntity.class));
    }

    @Test
    void shouldReturnEmptyPageWhenNoEventsFound() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<TicketEventEntity> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        doReturn(emptyPage).when(eventRepository).findAllByTicketId(TICKET_ID, pageable);

        Page<TicketEventResponse> actualResult = eventService.getTimeline(TICKET_ID, pageable);

        assertThat(actualResult.getContent()).isEmpty();
        assertThat(actualResult.getTotalElements()).isZero();
        verify(eventRepository).findAllByTicketId(TICKET_ID, pageable);
        verify(eventMapper, never()).map(any(TicketEventEntity.class));
    }

    @Test
    void shouldRecordCreatedEventSuccessfully() {
        TicketEntity ticket = TicketEntity.builder().id(TICKET_ID).build();
        UserEntity actor = UserEntity.builder().id(USER_ID).build();

        ArgumentCaptor<TicketEventEntity> argumentCaptor = ArgumentCaptor.forClass(TicketEventEntity.class);
        doReturn(mock(TicketEventEntity.class))
                .when(eventRepository).save(argumentCaptor.capture());

        eventService.recordCreatedEvent(ticket, actor);
        Map<String, Object> actualPayload = argumentCaptor.getValue().getPayload();

        assertThat(argumentCaptor.getValue().getEventType()).isEqualTo(TicketEventType.CREATED);
        assertThat(actualPayload.get("ticketId")).isEqualTo(TICKET_ID.toString());
        assertThat(actualPayload.get("createdById")).isEqualTo(USER_ID.toString());
        verify(eventRepository).save(any(TicketEventEntity.class));
    }

    @Test
    void shouldRecordAssignedEventSuccessfully() {
        TicketEntity ticket = TicketEntity.builder().id(TICKET_ID).build();
        UserEntity actor = UserEntity.builder().id(USER_ID).build();
        UserEntity previousAssignee = UserEntity.builder().id(2).build();
        UserEntity newAssignee = UserEntity.builder().id(3).build();

        ArgumentCaptor<TicketEventEntity> captor = ArgumentCaptor.forClass(TicketEventEntity.class);
        doReturn(mock(TicketEventEntity.class)).when(eventRepository).save(captor.capture());

        eventService.recordAssignedEvent(ticket, actor, previousAssignee, newAssignee);
        Map<String, Object> actualPayload = captor.getValue().getPayload();

        assertThat(captor.getValue().getEventType()).isEqualTo(TicketEventType.ASSIGNED);
        assertThat(actualPayload.get("previousAssigneeId")).isEqualTo("2");
        assertThat(actualPayload.get("assigneeId")).isEqualTo("3");
        verify(eventRepository).save(any(TicketEventEntity.class));
    }

    @Test
    void shouldRecordAssignedEventWithNoPreviousAssignee() {
        TicketEntity ticket = TicketEntity.builder().id(TICKET_ID).build();
        UserEntity actor = UserEntity.builder().id(USER_ID).build();
        UserEntity newAssignee = UserEntity.builder().id(3).build();

        ArgumentCaptor<TicketEventEntity> captor = ArgumentCaptor.forClass(TicketEventEntity.class);
        doReturn(mock(TicketEventEntity.class)).when(eventRepository).save(captor.capture());

        eventService.recordAssignedEvent(ticket, actor, null, newAssignee);
        Map<String, Object> actualPayload = captor.getValue().getPayload();

        assertThat(captor.getValue().getEventType()).isEqualTo(TicketEventType.ASSIGNED);
        assertThat(actualPayload.get("previousAssigneeId")).isNull();
        assertThat(actualPayload.get("assigneeId")).isEqualTo("3");
        verify(eventRepository).save(any(TicketEventEntity.class));
    }

    @Test
    void shouldRecordPriorityChangedEventSuccessfully() {
        TicketEntity ticket = TicketEntity.builder().id(TICKET_ID).build();
        UserEntity actor = UserEntity.builder().id(USER_ID).build();

        ArgumentCaptor<TicketEventEntity> captor = ArgumentCaptor.forClass(TicketEventEntity.class);
        doReturn(mock(TicketEventEntity.class)).when(eventRepository).save(captor.capture());

        eventService.recordPriorityChangedEvent(ticket, actor, TicketPriority.LOW, TicketPriority.HIGH);
        Map<String, Object> actualPayload = captor.getValue().getPayload();

        assertThat(captor.getValue().getEventType()).isEqualTo(TicketEventType.PRIORITY_CHANGED);
        assertThat(actualPayload.get("oldPriority")).isEqualTo(TicketPriority.LOW.name());
        assertThat(actualPayload.get("newPriority")).isEqualTo(TicketPriority.HIGH.name());
        verify(eventRepository).save(any(TicketEventEntity.class));
    }

    @Test
    void shouldRecordStatusChangedEventSuccessfully() {
        TicketEntity ticket = TicketEntity.builder().id(TICKET_ID).build();
        UserEntity actor = UserEntity.builder().id(USER_ID).build();

        ArgumentCaptor<TicketEventEntity> captor = ArgumentCaptor.forClass(TicketEventEntity.class);
        doReturn(mock(TicketEventEntity.class)).when(eventRepository).save(captor.capture());

        eventService.recordStatusChangedEvent(ticket, actor, TicketStatus.NEW, TicketStatus.IN_PROGRESS);
        Map<String, Object> actualPayload = captor.getValue().getPayload();

        assertThat(captor.getValue().getEventType()).isEqualTo(TicketEventType.STATUS_CHANGED);
        assertThat(actualPayload.get("oldStatus")).isEqualTo(TicketStatus.NEW.name());
        assertThat(actualPayload.get("newStatus")).isEqualTo(TicketStatus.IN_PROGRESS.name());
        verify(eventRepository).save(any(TicketEventEntity.class));
    }

    @Test
    void shouldRecordCommentedEventSuccessfully() {
        TicketEntity ticket = TicketEntity.builder().id(TICKET_ID).build();
        UserEntity actor = UserEntity.builder().id(USER_ID).build();

        ArgumentCaptor<TicketEventEntity> captor = ArgumentCaptor.forClass(TicketEventEntity.class);
        doReturn(mock(TicketEventEntity.class)).when(eventRepository).save(captor.capture());

        eventService.recordCommentedEvent(ticket, actor, COMMENT_ID);
        Map<String, Object> actualPayload = captor.getValue().getPayload();

        assertThat(captor.getValue().getEventType()).isEqualTo(TicketEventType.COMMENTED);
        assertThat(actualPayload.get("commentId")).isEqualTo(COMMENT_ID.toString());
        verify(eventRepository).save(any(TicketEventEntity.class));
    }

    @Test
    void shouldRecordCommentDeletedEventSuccessfully() {
        TicketEntity ticket = TicketEntity.builder().id(TICKET_ID).build();
        UserEntity actor = UserEntity.builder().id(USER_ID).build();

        ArgumentCaptor<TicketEventEntity> captor = ArgumentCaptor.forClass(TicketEventEntity.class);
        doReturn(mock(TicketEventEntity.class)).when(eventRepository).save(captor.capture());

        eventService.recordCommentDeletedEvent(ticket, actor, COMMENT_ID);
        Map<String, Object> actualPayload = captor.getValue().getPayload();

        assertThat(captor.getValue().getEventType()).isEqualTo(TicketEventType.COMMENT_DELETED);
        assertThat(actualPayload.get("commentId")).isEqualTo(COMMENT_ID.toString());
        verify(eventRepository).save(any(TicketEventEntity.class));
    }
}