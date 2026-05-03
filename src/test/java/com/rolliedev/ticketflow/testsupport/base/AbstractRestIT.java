package com.rolliedev.ticketflow.testsupport.base;

import com.rolliedev.ticketflow.repository.TicketRepository;
import com.rolliedev.ticketflow.repository.UserRepository;
import com.rolliedev.ticketflow.testsupport.annotation.IT;
import com.rolliedev.ticketflow.testsupport.container.AbstractPostgresContainerTest;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@IT
@AutoConfigureMockMvc
@Sql(scripts = {
        "classpath:sql/data.sql"
})
public abstract class AbstractRestIT extends AbstractPostgresContainerTest {

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected TicketRepository ticketRepository;
    @Autowired
    private EntityManager entityManager;

    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
