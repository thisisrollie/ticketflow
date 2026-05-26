package com.rolliedev.ticketflow.sla;

import com.rolliedev.ticketflow.dto.SlaPolicy;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@Component
@RequiredArgsConstructor
public class SlaPolicyProvider {

    private final JdbcTemplate jdbcTemplate;

    private Map<TicketPriority, SlaPolicy> slaPolicies;

    @PostConstruct
    public void init() {
        this.slaPolicies = jdbcTemplate.query("""
                                SELECT priority, first_response_due_minutes, resolution_due_minutes 
                                FROM sla_policies
                                """,
                        (rs, rowNum) -> new SlaPolicy(
                                TicketPriority.valueOf(rs.getString("priority")),
                                rs.getInt("first_response_due_minutes"),
                                rs.getInt("resolution_due_minutes")
                        )).stream()
                .collect(toMap(SlaPolicy::priority, identity()));

        validatePolicies(slaPolicies);
    }

    public SlaPolicy getSlaPolicy(TicketPriority priority) {
        if (priority == null) {
            throw new IllegalArgumentException("Ticket priority cannot be null");
        }
        return slaPolicies.get(priority);
    }

    private void validatePolicies(Map<TicketPriority, SlaPolicy> policies) {
        for (TicketPriority priority : TicketPriority.values()) {
            if (!policies.containsKey(priority)) {
                throw new IllegalStateException("No SLA policy found for priority: " + priority);
            }
        }
    }
}
