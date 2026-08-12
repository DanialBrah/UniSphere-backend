package com.unisphere.backend.campus.lostfound.controller;

import com.unisphere.backend.campus.lostfound.AbstractLostFoundIntegrationTest;
import com.unisphere.backend.campus.lostfound.dto.request.CreateLostFoundItemRequest;
import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The bounding-box prefilter, the Haversine post-filter, and the map viewport.
 *
 * <p>The MySQL container is shared across the whole run with no rollback between tests, so every
 * other test's items are still in the table. These assertions are count-based, so each test works
 * on its own <b>coordinate island</b> — a latitude band 1 degree (~111 km) away from its
 * neighbours, which is more than double the 50 km maximum radius. The geo predicate itself then
 * provides the isolation, which is a fair reflection of how the endpoint is actually used.
 */
class LostFoundGeoControllerTest extends AbstractLostFoundIntegrationTest {

    /** Islands are 1 degree apart in latitude; longitude is shared and irrelevant to isolation. */
    private static BigDecimal island(int index) {
        return new BigDecimal((10 + index) + ".0000000");
    }

    private static final BigDecimal LNG = new BigDecimal("101.5006000");

    @Test
    void nearby_returnsItemsWithinRadiusOrderedByDistance() throws Exception {
        String token = registerStudentAndGetToken("geo1@test.com", "GEO001");
        BigDecimal base = island(1);

        // ~1.1 km and ~0.11 km north of the query point respectively.
        reportLost(token, "Far item", base.add(new BigDecimal("0.0100000")), LNG);
        reportLost(token, "Near item", base.add(new BigDecimal("0.0010000")), LNG);

        mockMvc.perform(get(BASE + "/items/nearby")
                        .param("lat", base.toPlainString()).param("lng", LNG.toPlainString())
                        .param("radiusKm", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].item.title").value("Near item"))
                .andExpect(jsonPath("$.data.content[1].item.title").value("Far item"))
                .andExpect(jsonPath("$.data.content[0].distanceKm").isNumber());
    }

    @Test
    void nearby_excludesItemInBoundingBoxCornerOutsideRadius() throws Exception {
        String token = registerStudentAndGetToken("geo2@test.com", "GEO002");
        BigDecimal base = island(2);

        // A bounding box is a square and the radius is its inscribed circle, so the corners fall
        // outside. At this latitude 0.009 deg is ~1 km in both axes, so the diagonal point sits at
        // ~1.41 km — inside a 1 km box but outside a 1 km circle. It exists solely to prove the
        // Haversine post-filter runs.
        reportLost(token, "Corner item", base.add(new BigDecimal("0.0090000")),
                LNG.add(new BigDecimal("0.0090000")));
        reportLost(token, "Straight item", base.add(new BigDecimal("0.0080000")), LNG);

        mockMvc.perform(get(BASE + "/items/nearby")
                        .param("lat", base.toPlainString()).param("lng", LNG.toPlainString())
                        .param("radiusKm", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].item.title").value("Straight item"));
    }

    @Test
    void nearby_radiusAboveCapIsClamped() throws Exception {
        String token = registerStudentAndGetToken("geo3@test.com", "GEO003");
        BigDecimal base = island(3);
        reportLost(token, "Local item", base, LNG);

        // An over-wide radius is a slider at its end stop, not an error — clamped, not rejected.
        // Clamping to 50 km also keeps the result inside this island.
        mockMvc.perform(get(BASE + "/items/nearby")
                        .param("lat", base.toPlainString()).param("lng", LNG.toPlainString())
                        .param("radiusKm", "99999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void nearby_missingLat_returns400() throws Exception {
        String token = registerStudentAndGetToken("geo4@test.com", "GEO004");

        // A missing required parameter is a client error; without an explicit handler this would
        // fall through to the generic 500.
        mockMvc.perform(get(BASE + "/items/nearby")
                        .param("lng", LNG.toPlainString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_PARAMETER"));
    }

    @Test
    void nearby_unparseableLat_returns400() throws Exception {
        String token = registerStudentAndGetToken("geo4b@test.com", "GEO004B");

        mockMvc.perform(get(BASE + "/items/nearby")
                        .param("lat", "not-a-number").param("lng", LNG.toPlainString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETER"));
    }

    @Test
    void nearby_latitudeOutOfRange_returns400() throws Exception {
        String token = registerStudentAndGetToken("geo5@test.com", "GEO005");

        mockMvc.perform(get(BASE + "/items/nearby")
                        .param("lat", "101.5").param("lng", "3.06")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void nearby_clientSuppliedSortIsIgnored() throws Exception {
        String token = registerStudentAndGetToken("geo6@test.com", "GEO006");
        BigDecimal base = island(6);
        reportLost(token, "Sortable item", base, LNG);

        // Must be 200, not 500: Spring appends the raw property name to native SQL, and
        // ORDER BY createdAt is invalid against a created_at column.
        mockMvc.perform(get(BASE + "/items/nearby")
                        .param("lat", base.toPlainString()).param("lng", LNG.toPlainString())
                        .param("radiusKm", "5").param("sort", "createdAt")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void nearby_itemsWithoutCoordinatesAreExcluded() throws Exception {
        String token = registerStudentAndGetToken("geo7@test.com", "GEO007");
        BigDecimal base = island(7);
        reportItem(token, new CreateLostFoundItemRequest(
                LostFoundItemType.LOST, LostFoundCategory.OTHER, "Placeless item", "desc", null, null,
                "Somewhere vague", null, null, null, null, null, null,
                LocalDateTime.now().minusDays(1), null));

        mockMvc.perform(get(BASE + "/items/nearby")
                        .param("lat", base.toPlainString()).param("lng", LNG.toPlainString())
                        .param("radiusKm", "50")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void nearby_doesNotReturnSoftDeletedItems() throws Exception {
        String token = registerStudentAndGetToken("geo8@test.com", "GEO008");
        BigDecimal base = island(8);
        Long itemId = reportLost(token, "Doomed nearby item", base, LNG);
        mockMvc.perform(delete(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Native SQL bypasses @SQLRestriction — the query carries deleted_at IS NULL by hand.
        mockMvc.perform(get(BASE + "/items/nearby")
                        .param("lat", base.toPlainString()).param("lng", LNG.toPlainString())
                        .param("radiusKm", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void map_returnsPinsWithinViewport() throws Exception {
        String token = registerStudentAndGetToken("geo9@test.com", "GEO009");
        BigDecimal base = island(9);
        reportLost(token, "Inside the viewport", base, LNG);

        mockMvc.perform(get(BASE + "/items/map")
                        .param("minLat", base.subtract(BigDecimal.valueOf(0.1)).toPlainString())
                        .param("maxLat", base.add(BigDecimal.valueOf(0.1)).toPlainString())
                        .param("minLng", "101.4").param("maxLng", "101.6")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Inside the viewport"));
    }

    @Test
    void map_excludesPinsOutsideViewport() throws Exception {
        String token = registerStudentAndGetToken("geo10@test.com", "GEO010");
        BigDecimal base = island(10);
        // Placed on its own island, then queried with a viewport over a different, empty one.
        reportLost(token, "Outside the viewport", base, LNG);

        BigDecimal elsewhere = island(11);
        mockMvc.perform(get(BASE + "/items/map")
                        .param("minLat", elsewhere.subtract(BigDecimal.valueOf(0.1)).toPlainString())
                        .param("maxLat", elsewhere.add(BigDecimal.valueOf(0.1)).toPlainString())
                        .param("minLng", "101.4").param("maxLng", "101.6")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void map_invertedViewport_returns400() throws Exception {
        String token = registerStudentAndGetToken("geo11@test.com", "GEO011");

        // Rejected rather than silently returning nothing, which would look like "no items here".
        mockMvc.perform(get(BASE + "/items/map")
                        .param("minLat", "3.1").param("maxLat", "3.0")
                        .param("minLng", "101.4").param("maxLng", "101.6")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void map_antimeridianViewport_returns400() throws Exception {
        String token = registerStudentAndGetToken("geo12@test.com", "GEO012");

        mockMvc.perform(get(BASE + "/items/map")
                        .param("minLat", "3.0").param("maxLat", "3.1")
                        .param("minLng", "179.0").param("maxLng", "-179.0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void map_filtersByType() throws Exception {
        String student = registerStudentAndGetToken("geo13@test.com", "GEO013");
        String alumni = registerAlumniAndGetToken("geo13a@test.com");
        BigDecimal base = island(13);
        reportLost(student, "A lost pin", base, LNG);
        reportFound(alumni, "A found pin", base, LNG, "Security Post A");

        mockMvc.perform(get(BASE + "/items/map")
                        .param("minLat", base.subtract(BigDecimal.valueOf(0.1)).toPlainString())
                        .param("maxLat", base.add(BigDecimal.valueOf(0.1)).toPlainString())
                        .param("minLng", "101.4").param("maxLng", "101.6")
                        .param("type", "FOUND")
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].itemType").value("FOUND"));
    }
}
