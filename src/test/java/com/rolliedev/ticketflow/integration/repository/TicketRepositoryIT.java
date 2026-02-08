package com.rolliedev.ticketflow.integration.repository;

import com.rolliedev.ticketflow.config.AuditConfiguration;
import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
//@ImportAutoConfiguration(LiquibaseAutoConfiguration.class)
@Import(AuditConfiguration.class)
@RequiredArgsConstructor
class TicketRepositoryIT {

    private final TicketRepository ticketRepo;
    private final UserRepository userRepo;
    private final SpringLiquibase liquibase;

    @Test
    void checkIfLiquibaseIsEnabled() {
        assertThat(liquibase).isNotNull();
    }

    @Test
    void checkFindAllByStatusIn() {
        UserEntity customer = UserEntity.builder()
                .fullName("Clark Kent")
                .email("clark.kent@gmail.com")
                .role(Role.CUSTOMER)
                .build();
        userRepo.save(customer);
        TicketEntity ticket1 = TicketEntity.builder()
                .title("Can't log in")
                .description("Getting error when logging in with Google")
                .status(TicketStatus.IN_PROGRESS)
                .priority(TicketPriority.MEDIUM)
                .createdBy(customer)
                .build();
        TicketEntity ticket2 = TicketEntity.builder()
                .title("Billing discrepancy")
                .description("Charged twice for last month")
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.HIGH)
                .createdBy(customer)
                .build();
        TicketEntity ticket3 = TicketEntity.builder()
                .title("Cannot upgrade my subscription")
                .description("I want to upgrade my subscription to premium")
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.MEDIUM)
                .createdBy(customer)
                .build();
        ticketRepo.saveAll(List.of(ticket1, ticket2, ticket3));

        PageRequest pageable = PageRequest.of(0, 2, Sort.Direction.ASC, "id");
        Page<TicketEntity> page = ticketRepo.findAllByStatusIn(List.of(TicketStatus.IN_PROGRESS, TicketStatus.OPEN), pageable);
        List<TicketEntity> actualResult = page.getContent();

        assertThat(actualResult).hasSize(2);
        List<Long> ticketIds = actualResult.stream()
                .map(TicketEntity::getId)
                .toList();
        assertThat(ticketIds).containsExactly(ticket1.getId(), ticket2.getId());
    }
}