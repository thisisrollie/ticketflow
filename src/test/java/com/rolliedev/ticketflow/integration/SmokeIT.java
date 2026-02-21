package com.rolliedev.ticketflow.integration;

import com.rolliedev.ticketflow.testsupport.base.AbstractSpringBootIT;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class SmokeIT extends AbstractSpringBootIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @ValueSource(strings = {
            "users", "tickets", "ticket_comments", "ticket_events"
    })
    void checkIfTablesExist(String tableName) {
        Integer countResult = jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.tables " +
                                                          "WHERE table_schema = 'public' AND table_name = ?", Integer.class, tableName);
        assertThat(countResult).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "1, rollie, db.changelog-1.0.sql",
            "2, rollie, db.changelog-1.0.sql",
            "3, rollie, db.changelog-1.0.sql",
            "4, rollie, db.changelog-1.0.sql",
    })
    void checkIfChangesetsApplied(String id, String author, String filenameSuffix) {
        Integer actualResult = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM databasechangelog
                WHERE id = ?
                  AND author = ?
                  AND filename LIKE ?
                """, Integer.class, id, author, "%" + filenameSuffix);

        assertThat(actualResult).isEqualTo(1);
    }
}
