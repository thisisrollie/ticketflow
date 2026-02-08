package com.rolliedev.ticketflow.integration;

import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
@RequiredArgsConstructor
public class AuditIT {

    private final EntityManager entityManager;

    @Test
    void checkUserAudit() {
        UserEntity user = UserEntity.builder()
                .fullName("Clark Kent")
                .email("clark.kent@gmail.com")
                .role(Role.CUSTOMER)
                .build();

        entityManager.persist(user);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void checkTicketAudit() {
        UserEntity customer = UserEntity.builder()
                .fullName("Clark Kent")
                .email("clark.kent@gmail.com")
                .role(Role.CUSTOMER)
                .build();
        entityManager.persist(customer);

        TicketEntity ticket = TicketEntity.builder()
                .title("Test ticket")
                .description("Test ticket description")
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.MEDIUM)
                .createdBy(customer)
                .build();

        entityManager.persist(ticket);

        assertThat(ticket.getId()).isNotNull();
        assertThat(ticket.getCreatedAt()).isNotNull();
        assertThat(ticket.getModifiedAt()).isNotNull();
    }
}
