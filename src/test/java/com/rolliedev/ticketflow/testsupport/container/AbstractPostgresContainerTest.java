package com.rolliedev.ticketflow.testsupport.container;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class AbstractPostgresContainerTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @BeforeAll
    static void runContainer() {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void setPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
