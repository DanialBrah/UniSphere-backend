package com.unisphere.backend.campus.lostfound;

import com.unisphere.backend.campus.lostfound.dto.request.CreateLostFoundItemRequest;
import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemType;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Counterpart scoring: what gets suggested, what gets filtered out, and who may look.
 *
 * <p>The MySQL container is shared with no rollback between tests, and items reported without
 * coordinates always survive the candidate query's bounding box by design. So rather than asserting
 * on result counts, these tests assert on the <em>titles present</em> — each test tags its fixtures
 * with a unique marker and checks membership. That is also a more faithful assertion: the contract
 * is "this item is / is not suggested", not "exactly N things are".
 */
class LostFoundMatchServiceTest extends AbstractLostFoundIntegrationTest {

    private static BigDecimal island(int index) {
        return new BigDecimal((30 + index) + ".0000000");
    }

    private static final BigDecimal LNG = new BigDecimal("101.5006000");

    @Test
    void matches_scoresSameCategoryNearbyRecentCounterpartHighest() throws Exception {
        String lostOwner = registerStudentAndGetToken("mt1@test.com", "MT001");
        String finderA = registerAlumniAndGetToken("mt1a@test.com");
        String finderB = registerAlumniAndGetToken("mt1b@test.com");
        BigDecimal base = island(1);

        Long lostId = reportLost(lostOwner, "Lost black anker powerbank mt1marker", base, LNG);
        reportFound(finderA, "Found black anker powerbank mt1marker", base, LNG, "Security Post A");
        found(finderB, "Found green notebook mt1marker", LostFoundCategory.BOOKS,
                base.add(new BigDecimal("0.0500000")), LNG, 25);

        // The same-category, same-place, same-week counterpart must rank first.
        mockMvc.perform(get(BASE + "/items/{id}/matches", lostId)
                        .header("Authorization", "Bearer " + lostOwner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].item.title").value("Found black anker powerbank mt1marker"))
                .andExpect(jsonPath("$.data[0].score").isNumber())
                .andExpect(jsonPath("$.data[0].reasons[0]").value("Same category (ELECTRONICS)"));
    }

    @Test
    void matches_excludesSameTypeItems() throws Exception {
        String owner = registerStudentAndGetToken("mt2@test.com", "MT002");
        String other = registerStudentAndGetToken("mt2b@test.com", "MT002B");
        BigDecimal base = island(2);
        Long lostId = reportLost(owner, "Lost black powerbank mt2marker", base, LNG);
        reportLost(other, "Lost black powerbank too mt2marker", base, LNG);

        // Two people losing the same thing is not a match.
        assertThat(matchTitles(owner, lostId))
                .noneMatch(title -> title.contains("mt2marker"));
    }

    @Test
    void matches_excludesOwnItems() throws Exception {
        String owner = registerAlumniAndGetToken("mt3@test.com");
        BigDecimal base = island(3);
        Long lostId = reportLost(owner, "Lost black powerbank mt3marker", base, LNG);
        reportFound(owner, "Found black powerbank mt3marker", base, LNG, "Security Post A");

        assertThat(matchTitles(owner, lostId))
                .noneMatch(title -> title.contains("mt3marker"));
    }

    @Test
    void matches_excludesItemsOutsideDateWindow() throws Exception {
        String owner = registerStudentAndGetToken("mt4@test.com", "MT004");
        String finder = registerAlumniAndGetToken("mt4a@test.com");
        BigDecimal base = island(4);
        Long lostId = reportLost(owner, "Lost black powerbank mt4marker", base, LNG);
        // Default window is 30 days either side; 200 days back is well outside it.
        found(finder, "Found black powerbank mt4marker", LostFoundCategory.ELECTRONICS, base, LNG, 200);

        assertThat(matchTitles(owner, lostId))
                .noneMatch(title -> title.contains("mt4marker"));
    }

    @Test
    void matches_excludesNonOpenItems() throws Exception {
        String owner = registerStudentAndGetToken("mt5@test.com", "MT005");
        String finder = registerAlumniAndGetToken("mt5a@test.com");
        BigDecimal base = island(5);
        Long lostId = reportLost(owner, "Lost black powerbank mt5marker", base, LNG);
        Long foundId = reportFound(finder, "Found black powerbank mt5marker", base, LNG, "Security Post A");
        changeItemStatus(finder, foundId, LostFoundItemStatus.RESOLVED);

        assertThat(matchTitles(owner, lostId))
                .noneMatch(title -> title.contains("mt5marker"));
    }

    @Test
    void matches_includeItemsWithoutCoordinates() throws Exception {
        String owner = registerStudentAndGetToken("mt6@test.com", "MT006");
        String finder = registerAlumniAndGetToken("mt6a@test.com");
        BigDecimal base = island(6);
        Long lostId = reportLost(owner, "Lost black anker powerbank mt6marker", base, LNG);

        // A wallet described only in text is still a valid match — scored down, never dropped.
        reportItem(finder, new CreateLostFoundItemRequest(
                LostFoundItemType.FOUND, LostFoundCategory.ELECTRONICS,
                "Found black anker powerbank mt6marker", "No idea where exactly", null, null,
                null, null, null, "Security Post A", null, null, null,
                LocalDateTime.now().minusDays(1), null));

        assertThat(matchTitles(owner, lostId))
                .contains("Found black anker powerbank mt6marker");
    }

    @Test
    void matches_byNonOwner_returns403() throws Exception {
        String owner = registerStudentAndGetToken("mt7@test.com", "MT007");
        String stranger = registerStudentAndGetToken("mt7s@test.com", "MT007S");
        Long lostId = reportLost(owner, "Lost black powerbank mt7marker", island(7), LNG);

        // A shortlist of FOUND items matching a stranger's missing wallet is a fraud roadmap.
        mockMvc.perform(get(BASE + "/items/{id}/matches", lostId)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void matches_byAdmin_isAllowed() throws Exception {
        String owner = registerStudentAndGetToken("mt8@test.com", "MT008");
        String admin = registerAdminAndGetToken("mt8a@test.com");
        Long lostId = reportLost(owner, "Lost black powerbank mt8marker", island(8), LNG);

        mockMvc.perform(get(BASE + "/items/{id}/matches", lostId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    /** The titles of every suggestion returned for an item. */
    private List<String> matchTitles(String token, Long itemId) throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/items/{id}/matches", itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        List<String> titles = new ArrayList<>();
        objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data")
                .forEach(node -> titles.add(node.at("/item/title").asText()));
        return titles;
    }

    private void found(String token, String title, LostFoundCategory category,
                       BigDecimal lat, BigDecimal lng, int daysAgo) throws Exception {
        reportItem(token, new CreateLostFoundItemRequest(
                LostFoundItemType.FOUND, category, title, "A test description for " + title,
                null, null, "Test incident place", lat, lng,
                "Security Post A", lat, lng, null,
                LocalDateTime.now().minusDays(daysAgo), null));
    }
}
