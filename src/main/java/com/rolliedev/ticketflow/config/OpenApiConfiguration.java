package com.rolliedev.ticketflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI ticketFlowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TicketFlow API")
                        .version("1.0.0")
                        .description("REST API for TicketFlow helpdesk ticket management platform."));
    }
}
