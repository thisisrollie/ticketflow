package com.rolliedev.ticketflow.integration.http.controller;

import com.rolliedev.ticketflow.testsupport.base.AbstractSpringBootIT;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.collection.IsCollectionWithSize;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@RequiredArgsConstructor
class CommentControllerIT extends AbstractSpringBootIT {

    private static final String TICKET_ID = "1";
    private static final String COMMENT_ID = "1";
    private static final String CUSTOMER_ID = "3";

    private final MockMvc mockMvc;

    @Test
    void shouldRedirectCorrectlyAfterCreation() throws Exception {
        mockMvc.perform(post("/tickets/{ticketId}/comments", TICKET_ID)
                        .param("authorId", CUSTOMER_ID)
                        .param("text", "some comment ...")
                )
                .andExpectAll(
                        status().is3xxRedirection(),
                        redirectedUrl("/tickets/" + TICKET_ID)
                );
    }

    @Test
    void shouldRedirectWithFlashAttributeAfterValidationFailureDuringCreate() throws Exception {
        mockMvc.perform(post("/tickets/{ticketId}/comments", TICKET_ID)
                        .param("authorId", StringUtils.EMPTY)
                        .param("text", StringUtils.EMPTY)
                )
                .andExpectAll(
                        status().is3xxRedirection(),
                        flash().attributeExists("errors"),
                        flash().attribute("errors", IsCollectionWithSize.hasSize(2)),
                        redirectedUrl("/tickets/" + TICKET_ID)
                );
    }

    @Test
    void shouldRedirectCorrectlyAfterDeletion() throws Exception {
        mockMvc.perform(post("/tickets/{ticketId}/comments/{commentId}/delete", TICKET_ID, COMMENT_ID)
                        .param("actorId", CUSTOMER_ID)
                )
                .andExpectAll(
                        status().is3xxRedirection(),
                        redirectedUrl("/tickets/" + TICKET_ID)
                );
    }

    @Test
    void shouldRedirectWithFlashAttributeAfterDeletionFailure() throws Exception {
        mockMvc.perform(post("/tickets/{ticketId}/comments/{commentId}/delete", "99999", COMMENT_ID)
                        .param("actorId", CUSTOMER_ID)
                )
                .andExpectAll(
                        status().is3xxRedirection(),
                        flash().attributeExists("error"),
                        flash().attribute("error", "Comment does not belong to the given ticket"),
                        redirectedUrl("/tickets")
                );
    }

    @Test
    void shouldRedirectWithFlashAttributeAfterValidationFailureDuringDelete() throws Exception {
        mockMvc.perform(post("/tickets/{ticketId}/comments/{commentId}/delete", TICKET_ID, COMMENT_ID)
                        .param("actorId", StringUtils.EMPTY)
                )
                .andExpectAll(
                        status().is3xxRedirection(),
                        flash().attributeExists("errors"),
                        redirectedUrl("/tickets/" + TICKET_ID)
                );
    }
}