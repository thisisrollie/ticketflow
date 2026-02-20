package com.rolliedev.ticketflow.integration;

import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.integration.annotation.IT;
import com.rolliedev.ticketflow.repository.TicketEventRepository;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;

@IT
@SqlGroup({
        @Sql(scripts = "classpath:sql/data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(scripts = "classpath:sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
})
public abstract class IntegrationTestBase {

    @Autowired
    protected UserRepository userRepo;
    @Autowired
    protected TicketRepository ticketRepo;
    @Autowired
    protected TicketEventRepository eventRepo;
    @Autowired
    protected EntityManager entityManager;

    protected UserEntity admin, agent, customer;
    protected TicketEntity ticket1, ticket2, ticket3;

    @BeforeEach
    void setUp() {
        admin = userRepo.findByEmail("lex.luthor@gmail.com").orElseThrow();
        agent = userRepo.findByEmail("bruce.wayne@gmail.com").orElseThrow();
        customer = userRepo.findByEmail("clark.kent@gmail.com").orElseThrow();

        ticket1 = ticketRepo.findById(1L).orElseThrow();
        ticket2 = ticketRepo.findById(2L).orElseThrow();
        ticket3 = ticketRepo.findById(3L).orElseThrow();
    }

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
