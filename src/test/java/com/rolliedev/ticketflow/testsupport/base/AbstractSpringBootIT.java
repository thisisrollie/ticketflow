package com.rolliedev.ticketflow.testsupport.base;

import com.rolliedev.ticketflow.entity.TicketEntity;
import com.rolliedev.ticketflow.entity.UserEntity;
import com.rolliedev.ticketflow.repository.TicketEventRepository;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.testsupport.annotation.IT;
import com.rolliedev.ticketflow.testsupport.container.AbstractPostgresContainerTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IT
@Sql(scripts = {
        "classpath:sql/data.sql"
})
public abstract class AbstractSpringBootIT extends AbstractPostgresContainerTest {

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
