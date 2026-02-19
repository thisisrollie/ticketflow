package com.rolliedev.ticketflow.integration.repository;

import com.querydsl.core.types.Predicate;
import com.rolliedev.ticketflow.dto.TicketSearchFilter;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.integration.annotation.JpaIT;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.util.DataUtils;
import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JpaIT
@RequiredArgsConstructor
class TicketRepositoryIT {

    private final TicketRepository ticketRepo;
    private final UserRepository userRepo;
    private final SpringLiquibase liquibase;

    private UserEntity customer;
    private TicketEntity ticket1, ticket2, ticket3;

    @BeforeEach
    void setUp() {
        customer = DataUtils.getTransientUser("Clark", "Kent", Role.CUSTOMER);
        userRepo.saveAndFlush(customer);

        ticket1 = DataUtils.getTransientTicket("Can't log in", "Getting error when logging in with Google", TicketStatus.IN_PROGRESS, TicketPriority.MEDIUM, customer, null);
        ticket2 = DataUtils.getTransientTicket("Billing discrepancy", "Charged twice for last month", TicketStatus.NEW, TicketPriority.HIGH, customer, null);
        ticket3 = DataUtils.getTransientTicket("Cannot upgrade my subscription", "I want to upgrade my subscription to premium", TicketStatus.NEW, TicketPriority.MEDIUM, customer, null);
        ticketRepo.saveAllAndFlush(List.of(ticket1, ticket2, ticket3));
    }

    @Test
    void checkIfLiquibaseIsEnabled() {
        assertThat(liquibase).isNotNull();
    }

    @Test
    void shouldReturnFirstPageWhenFilteringByMultipleStatuses() {
        PageRequest pageable = PageRequest.of(0, 2, Sort.Direction.ASC, "id");

        Page<TicketEntity> page = ticketRepo.findAllByStatusIn(List.of(TicketStatus.IN_PROGRESS, TicketStatus.NEW), pageable);

        assertThat(page.getContent()).hasSize(2);
        List<Long> ticketIds = page.getContent().stream()
                .map(TicketEntity::getId)
                .toList();
        assertThat(ticketIds).containsExactly(ticket1.getId(), ticket2.getId());
    }

    @Test
    void shouldReturnMatchingTicketsWhenFilterByStatusAndCreatorId() {
        Predicate predicate = TicketSearchFilter.buildPredicate(
                TicketSearchFilter.builder()
                        .status(TicketStatus.NEW)
                        .creatorId(customer.getId())
                        .build()
        );

        Iterable<TicketEntity> actualResult = ticketRepo.findAll(predicate);

        assertThat(actualResult).hasSize(2);
        assertThat(actualResult).allSatisfy(ticket -> {
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NEW);
            assertThat(ticket.getCreatedBy().getId()).isEqualTo(customer.getId());
        });
        assertThat(actualResult)
                .extracting(TicketEntity::getId)
                .contains(ticket2.getId(), ticket3.getId());
    }

    @Test
    void shouldReturnSortedPageWhenFilterByCreatorId() {
        Predicate predicate = TicketSearchFilter.buildPredicate(
                TicketSearchFilter.builder()
                        .creatorId(customer.getId())
                        .build()
        );
        PageRequest pageable = PageRequest.of(0, 2, Sort.Direction.DESC, "id");

        Page<TicketEntity> page = ticketRepo.findAll(predicate, pageable);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent())
                .allSatisfy(ticket -> assertThat(ticket.getCreatedBy().getId()).isEqualTo(customer.getId()));
        assertThat(page.getContent())
                .extracting(TicketEntity::getId)
                .containsExactly(ticket3.getId(), ticket2.getId());
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void shouldReturnMatchingTicketsWhenFilterByKeyword() {
        TicketEntity ticket4 = DataUtils.getTransientTicket("Can't log in (YouTube)", "Getting error when logging in with Google", TicketStatus.CLOSED, TicketPriority.MEDIUM, customer, null);
        TicketEntity ticket5 = DataUtils.getTransientTicket("Billing discrepancy", "YouTube charged me twice for last month", TicketStatus.IN_PROGRESS, TicketPriority.CRITICAL, customer, null);
        ticketRepo.saveAllAndFlush(List.of(ticket4, ticket5));

        Predicate predicate = TicketSearchFilter.buildPredicate(
                TicketSearchFilter.builder()
                        .keyword("youtube")
                        .build()
        );

        Iterable<TicketEntity> actualResult = ticketRepo.findAll(predicate);

        assertThat(actualResult).hasSize(2);
        assertThat(actualResult)
                .extracting(TicketEntity::getId)
                .containsExactly(ticket4.getId(), ticket5.getId());
    }
}