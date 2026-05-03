package com.rolliedev.ticketflow.testsupport.base;

import com.rolliedev.ticketflow.repository.TicketEventRepository;
import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.testsupport.annotation.IT;
import com.rolliedev.ticketflow.testsupport.container.AbstractPostgresContainerTest;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IT
@Sql(scripts = {
        "classpath:sql/data.sql"
})
public abstract class AbstractSpringBootIT extends AbstractPostgresContainerTest {

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected TicketRepository ticketRepository;
    @Autowired
    protected TicketEventRepository eventRepository;
    @Autowired
    protected EntityManager entityManager;

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
