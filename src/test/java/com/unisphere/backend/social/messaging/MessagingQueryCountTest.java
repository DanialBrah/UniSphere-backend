package com.unisphere.backend.social.messaging;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards against N+1 regressions in the messaging list endpoints — see PostQueryCountTest for why
 * these assert on growth rather than an absolute count.
 *
 * The inbox was the worst offender before batching: every conversation cost a members query, a
 * user lookup per member, a last-message query and a sender lookup.
 */
class MessagingQueryCountTest extends AbstractMessagingIntegrationTest {

    private static final long ALLOWED_GROWTH = 5;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private long countQueries(Action action) throws Exception {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        action.run();
        return stats.getPrepareStatementCount();
    }

    @Test
    void inbox_queryCountDoesNotScaleWithPageSize() throws Exception {
        String owner = registerStudentAndGetToken("qc.inbox.owner@test.com", "QCI1000");

        // Distinct counterparties so member and sender lookups can't collapse to a single user.
        for (int i = 0; i < 10; i++) {
            String other = registerStudentAndGetToken("qc.inbox." + i + "@test.com", "QCI200" + i);
            Long otherId = getUserId(other);
            Long convId = createDirectConversation(owner, otherId);
            sendMessage(other, convId, "Last message in conversation " + i);
        }

        long small = countQueries(() ->
                mockMvc.perform(get("/api/v1/conversations?page=0&size=2")
                                .header("Authorization", "Bearer " + owner))
                        .andExpect(status().isOk()));

        long large = countQueries(() ->
                mockMvc.perform(get("/api/v1/conversations?page=0&size=10")
                                .header("Authorization", "Bearer " + owner))
                        .andExpect(status().isOk()));

        assertThat(large - small)
                .as("inbox queries grew from %d to %d when the page went from 2 to 10 conversations "
                        + "— a per-conversation lookup has been reintroduced", small, large)
                .isLessThanOrEqualTo(ALLOWED_GROWTH);
    }

    @Test
    void messageHistory_queryCountDoesNotScaleWithPageSize() throws Exception {
        String owner = registerStudentAndGetToken("qc.history.owner@test.com", "QCH1000");
        String other = registerStudentAndGetToken("qc.history.other@test.com", "QCH1001");
        Long convId = createDirectConversation(owner, getUserId(other));

        // Alternate senders so the sender lookups are not all the same user.
        for (int i = 0; i < 5; i++) {
            sendMessage(owner, convId, "From owner " + i);
            sendMessage(other, convId, "From other " + i);
        }

        long small = countQueries(() ->
                mockMvc.perform(get("/api/v1/messages?conversationId={id}&page=0&size=2", convId)
                                .header("Authorization", "Bearer " + owner))
                        .andExpect(status().isOk()));

        long large = countQueries(() ->
                mockMvc.perform(get("/api/v1/messages?conversationId={id}&page=0&size=10", convId)
                                .header("Authorization", "Bearer " + owner))
                        .andExpect(status().isOk()));

        assertThat(large - small)
                .as("message history queries grew from %d to %d when the page went from 2 to 10 "
                        + "messages — a per-message lookup has been reintroduced", small, large)
                .isLessThanOrEqualTo(ALLOWED_GROWTH);
    }
}
