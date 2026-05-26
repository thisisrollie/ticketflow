package com.rolliedev.ticketflow.dto;

import com.rolliedev.ticketflow.entity.enums.SlaStatus;
import com.rolliedev.ticketflow.entity.enums.TicketPriority;
import com.rolliedev.ticketflow.entity.enums.TicketStatus;
import lombok.Builder;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;

@Builder
public record TicketSearchFilter(String keyword,
                                 TicketStatus status,
                                 TicketPriority priority,
                                 Integer creatorId,
                                 Integer assigneeId,
                                 LocalDate createdBefore,
                                 LocalDate createdAfter,
                                 SlaStatus responseSlaStatus,
                                 SlaStatus resolutionSlaStatus) {

    public String toQueryString() {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();

        if (keyword != null && !keyword.isBlank()) {
            builder.queryParam("keyword", keyword);
        }
        if (status != null) {
            builder.queryParam("status", status.name());
        }
        if (priority != null) {
            builder.queryParam("priority", priority.name());
        }
        if (creatorId != null) {
            builder.queryParam("creatorId", creatorId);
        }
        if (assigneeId != null) {
            builder.queryParam("assigneeId", assigneeId);
        }
        if (createdBefore != null) {
            builder.queryParam("createdBefore", createdBefore);
        }
        if (createdAfter != null) {
            builder.queryParam("createdAfter", createdAfter);
        }
        if (responseSlaStatus != null) {
            builder.queryParam("responseSlaStatus", responseSlaStatus.name());
        }
        if (resolutionSlaStatus != null) {
            builder.queryParam("resolutionSlaStatus", resolutionSlaStatus.name());
        }

        String query = builder.build().encode().toUriString();
        return query.isBlank() ? "" : "&" + query.substring(1);
    }
}
