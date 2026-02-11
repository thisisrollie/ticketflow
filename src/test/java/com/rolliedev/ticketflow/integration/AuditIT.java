package com.rolliedev.ticketflow.integration;

import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.entity.enums.Role;
import com.rolliedev.ticketflow.util.DataUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@Transactional
@SpringBootTest
@RequiredArgsConstructor
public class AuditIT {

    private final EntityManager entityManager;

    @Test
    void checkUserAudit() {
        UserEntity user = DataUtils.getTransientUser("Clark", "Kent", Role.CUSTOMER);

        entityManager.persist(user);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void checkTicketAudit() {
        UserEntity customer = DataUtils.getTransientUser("Clark", "Kent", Role.CUSTOMER);
        entityManager.persist(customer);

        TicketEntity ticket = DataUtils.getTransientTicket("Test ticket", "Test ticket description", customer);
        entityManager.persist(ticket);

        assertThat(ticket.getId()).isNotNull();
        assertThat(ticket.getCreatedAt()).isNotNull();
        assertThat(ticket.getModifiedAt()).isNotNull();
    }
}
