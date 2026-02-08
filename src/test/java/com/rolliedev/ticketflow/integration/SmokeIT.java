package com.rolliedev.ticketflow.integration;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@RequiredArgsConstructor
public class SmokeIT {

    private final JdbcTemplate jdbcTemplate;

    @ParameterizedTest
    @ValueSource(strings = {
            "users", "tickets"
    })
    void checkIfTablesExist(String tableName) {
        Integer countResult = jdbcTemplate.queryForObject("SELECT count(*) FROM information_schema.tables " +
                                                          "WHERE table_schema = 'public' AND table_name = ?", Integer.class, tableName);
        assertThat(countResult).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "1, rollie, db.changelog-1.0.sql",
            "2, rollie, db.changelog-1.0.sql"
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
