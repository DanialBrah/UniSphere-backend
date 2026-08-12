package com.unisphere.backend.campus.lostfound.controller;

import com.unisphere.backend.campus.lostfound.AbstractLostFoundIntegrationTest;
import com.unisphere.backend.campus.lostfound.dto.request.CreateLostFoundItemRequest;
import com.unisphere.backend.campus.lostfound.dto.request.LostFoundMediaItem;
import com.unisphere.backend.campus.lostfound.dto.request.UpdateLostFoundItemRequest;
import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemType;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LostFoundItemControllerTest extends AbstractLostFoundIntegrationTest {

    @Test
    void createItem_validLostRequest_returns201() throws Exception {
        String token = registerStudentAndGetToken("it1@test.com", "IT001");

        mockMvc.perform(post(BASE + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(lost("Lost umbrella", CAMPUS_LAT, CAMPUS_LNG))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.itemType").value("LOST"))
                .andExpect(jsonPath("$.data.canModify").value(true));
    }

    @Test
    void createItem_foundWithoutPickupLocation_returns400() throws Exception {
        String token = registerAlumniAndGetToken("it2@test.com");
        CreateLostFoundItemRequest req = new CreateLostFoundItemRequest(
                LostFoundItemType.FOUND, LostFoundCategory.BAGS, "Found bag", "desc", null, null,
                "Cafeteria", CAMPUS_LAT, CAMPUS_LNG,
                null, null, null, null,
                LocalDateTime.now().minusDays(1), null);

        // "Where can I collect it" is the entire point of a found report.
        mockMvc.perform(post(BASE + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void createItem_latitudeWithoutLongitude_returns400() throws Exception {
        String token = registerStudentAndGetToken("it3@test.com", "IT003");
        CreateLostFoundItemRequest req = new CreateLostFoundItemRequest(
                LostFoundItemType.LOST, LostFoundCategory.OTHER, "Half a coordinate", "desc", null, null,
                "Somewhere", CAMPUS_LAT, null,
                null, null, null, null,
                LocalDateTime.now().minusDays(1), null);

        mockMvc.perform(post(BASE + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createItem_latitudeOutOfRange_returns400WithValidationErrorCode() throws Exception {
        String token = registerStudentAndGetToken("it4@test.com", "IT004");
        // A swapped lat/lng pair — caught at the edge rather than dropping a pin in the ocean.
        CreateLostFoundItemRequest req = new CreateLostFoundItemRequest(
                LostFoundItemType.LOST, LostFoundCategory.OTHER, "Swapped pair", "desc", null, null,
                "Somewhere", new BigDecimal("101.5006000"), new BigDecimal("3.0678000"),
                null, null, null, null,
                LocalDateTime.now().minusDays(1), null);

        mockMvc.perform(post(BASE + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createItem_tooManyDecimalPlaces_returns400() throws Exception {
        String token = registerStudentAndGetToken("it5@test.com", "IT005");
        // Without @Digits this would be silently truncated by MySQL or 500 on data truncation.
        CreateLostFoundItemRequest req = new CreateLostFoundItemRequest(
                LostFoundItemType.LOST, LostFoundCategory.OTHER, "Too precise", "desc", null, null,
                "Somewhere", new BigDecimal("3.06784311234567"), new BigDecimal("101.5006219"),
                null, null, null, null,
                LocalDateTime.now().minusDays(1), null);

        mockMvc.perform(post(BASE + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createItem_occurredAtInFuture_returns400() throws Exception {
        String token = registerStudentAndGetToken("it6@test.com", "IT006");
        CreateLostFoundItemRequest req = new CreateLostFoundItemRequest(
                LostFoundItemType.LOST, LostFoundCategory.OTHER, "Time traveller", "desc", null, null,
                "Somewhere", CAMPUS_LAT, CAMPUS_LNG,
                null, null, null, null,
                LocalDateTime.now().plusDays(3), null);

        mockMvc.perform(post(BASE + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createItem_mediaKeyOwnedByAnotherUser_returns403() throws Exception {
        String owner = registerStudentAndGetToken("it7a@test.com", "IT007A");
        String other = registerStudentAndGetToken("it7b@test.com", "IT007B");
        Long ownerId = getUserId(owner);

        CreateLostFoundItemRequest req = new CreateLostFoundItemRequest(
                LostFoundItemType.LOST, LostFoundCategory.OTHER, "Borrowed media", "desc", null, null,
                "Somewhere", CAMPUS_LAT, CAMPUS_LNG,
                null, null, null, null,
                LocalDateTime.now().minusDays(1),
                List.of(new LostFoundMediaItem("lost-found/" + ownerId + "/not-yours.jpg", "IMAGE")));

        // Ownership is checked on attach, not only on delete.
        mockMvc.perform(post(BASE + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + other)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void createItem_universityIdIsDerivedFromReporterNotRequest() throws Exception {
        String uniToken = registerUniversityAndGetToken("it8u@test.com", "Test University 8");
        Long universityId = getUserId(uniToken);
        String student = registerStudentAndGetToken("it8s@test.com", "IT008");
        setStudentUniversityId(getUserId(student), universityId);

        // The request record has no universityId field at all — this pins that the value comes from
        // the reporter's own affiliation.
        mockMvc.perform(post(BASE + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + student)
                        .content(objectMapper.writeValueAsString(lost("Scoped item", CAMPUS_LAT, CAMPUS_LNG))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.universityId").value(universityId));
    }

    @Test
    void listItems_hidesOtherUniversityItemsFromAffiliatedViewer() throws Exception {
        Long uniA = getUserId(registerUniversityAndGetToken("it9a@test.com", "University 9A"));
        Long uniB = getUserId(registerUniversityAndGetToken("it9b@test.com", "University 9B"));

        String studentA = registerStudentAndGetToken("it9sa@test.com", "IT009A");
        String studentB = registerStudentAndGetToken("it9sb@test.com", "IT009B");
        setStudentUniversityId(getUserId(studentA), uniA);
        setStudentUniversityId(getUserId(studentB), uniB);

        reportLost(studentA, "Campus A only zzzuniqueA", CAMPUS_LAT, CAMPUS_LNG);

        mockMvc.perform(get(BASE + "/items/search").param("q", "zzzuniqueA")
                        .header("Authorization", "Bearer " + studentB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void listItems_adminSeesEveryUniversity() throws Exception {
        Long uniA = getUserId(registerUniversityAndGetToken("it10a@test.com", "University 10A"));
        String studentA = registerStudentAndGetToken("it10sa@test.com", "IT010A");
        setStudentUniversityId(getUserId(studentA), uniA);
        String admin = registerAdminAndGetToken("it10adm@test.com");

        reportLost(studentA, "Campus A only zzzuniqueB", CAMPUS_LAT, CAMPUS_LNG);

        mockMvc.perform(get(BASE + "/items/search").param("q", "zzzuniqueB")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void listItems_filtersByTypeStatusCategory() throws Exception {
        String token = registerStudentAndGetToken("it11@test.com", "IT011");
        // PETS is used by no other test, which isolates these counts on the shared container.
        reportItem(token, new CreateLostFoundItemRequest(
                LostFoundItemType.LOST, LostFoundCategory.PETS, "Filterable lost pet", "desc", null, null,
                "Test incident place", CAMPUS_LAT, CAMPUS_LNG, null, null, null, null,
                LocalDateTime.now().minusDays(1), null));

        mockMvc.perform(get(BASE + "/items")
                        .param("type", "FOUND").param("status", "OPEN").param("category", "PETS")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get(BASE + "/items")
                        .param("type", "LOST").param("status", "OPEN").param("category", "PETS")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Filterable lost pet"));
    }

    @Test
    void getItem_softDeleted_returns404() throws Exception {
        String token = registerStudentAndGetToken("it12@test.com", "IT012");
        Long itemId = reportLost(token, "Doomed item", CAMPUS_LAT, CAMPUS_LNG);

        mockMvc.perform(delete(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 404 rather than 403 — a 403 would confirm the row exists.
        mockMvc.perform(get(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LOST_FOUND_ITEM_NOT_FOUND"));
    }

    @Test
    void updateItem_byNonOwner_returns403() throws Exception {
        String owner = registerStudentAndGetToken("it13a@test.com", "IT013A");
        String other = registerStudentAndGetToken("it13b@test.com", "IT013B");
        Long itemId = reportLost(owner, "Someone else's item", CAMPUS_LAT, CAMPUS_LNG);

        mockMvc.perform(put(BASE + "/items/{id}", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + other)
                        .content(objectMapper.writeValueAsString(emptyUpdate("Hijacked"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateItem_partialPatchLeavesOtherFieldsUntouched() throws Exception {
        String token = registerStudentAndGetToken("it14@test.com", "IT014");
        Long itemId = reportLost(token, "Original title", CAMPUS_LAT, CAMPUS_LNG);

        mockMvc.perform(put(BASE + "/items/{id}", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(emptyUpdate("Updated title"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Updated title"))
                // Compared as a double: jsonPath deserialises a JSON number to Double, so
                // BigDecimal("3.0678000") would fail against 3.0678 on scale alone.
                .andExpect(jsonPath("$.data.incidentLatitude").value(CAMPUS_LAT.doubleValue()))
                .andExpect(jsonPath("$.data.incidentPlace").value("Test incident place"));
    }

    @Test
    void updateItem_clearIncidentLocationNullsBothCoordinates() throws Exception {
        String token = registerStudentAndGetToken("it15@test.com", "IT015");
        Long itemId = reportLost(token, "Located item", CAMPUS_LAT, CAMPUS_LNG);

        UpdateLostFoundItemRequest req = new UpdateLostFoundItemRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                Boolean.TRUE, null, null, null);

        mockMvc.perform(put(BASE + "/items/{id}", itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.incidentLatitude").doesNotExist())
                .andExpect(jsonPath("$.data.incidentLongitude").doesNotExist());
    }

    @Test
    void deleteItem_byOwner_softDeletesAndDisappearsFromFeed() throws Exception {
        String token = registerStudentAndGetToken("it16@test.com", "IT016");
        Long itemId = reportLost(token, "Vanishing zzzdeleted item", CAMPUS_LAT, CAMPUS_LNG);

        mockMvc.perform(delete(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE + "/items/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void deleteItem_byAdmin_succeeds() throws Exception {
        String owner = registerStudentAndGetToken("it17@test.com", "IT017");
        String admin = registerAdminAndGetToken("it17a@test.com");
        Long itemId = reportLost(owner, "Admin-removable item", CAMPUS_LAT, CAMPUS_LNG);

        mockMvc.perform(delete(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void search_matchesTitleAndDescription() throws Exception {
        String token = registerStudentAndGetToken("it18@test.com", "IT018");
        reportLost(token, "Lost zzzcalculator device", CAMPUS_LAT, CAMPUS_LNG);

        mockMvc.perform(get(BASE + "/items/search").param("q", "zzzcalculator")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void search_shortTermReturnsEmpty() throws Exception {
        String token = registerStudentAndGetToken("it19@test.com", "IT019");
        reportLost(token, "An item about ab", CAMPUS_LAT, CAMPUS_LNG);

        // innodb_ft_min_token_size defaults to 3, so 1- and 2-character terms match nothing.
        mockMvc.perform(get(BASE + "/items/search").param("q", "ab")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void search_doesNotReturnSoftDeletedItems() throws Exception {
        String token = registerStudentAndGetToken("it20@test.com", "IT020");
        Long itemId = reportLost(token, "Lost zzzephemeral thing", CAMPUS_LAT, CAMPUS_LNG);
        mockMvc.perform(delete(BASE + "/items/{id}", itemId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Native SQL bypasses @SQLRestriction, so the query carries deleted_at IS NULL by hand.
        mockMvc.perform(get(BASE + "/items/search").param("q", "zzzephemeral")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void search_clientSuppliedSortIsIgnored() throws Exception {
        String token = registerStudentAndGetToken("it21@test.com", "IT021");
        reportLost(token, "Sortable zzzsortitem", CAMPUS_LAT, CAMPUS_LNG);

        // Spring would otherwise append ORDER BY createdAt to native SQL against a created_at column.
        mockMvc.perform(get(BASE + "/items/search")
                        .param("q", "zzzsortitem").param("sort", "createdAt")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    /**
     * Stats are a whole-board aggregate and the container is shared, so this asserts on the
     * <em>delta</em> across a known set of writes rather than on absolute totals.
     */
    @Test
    void stats_returnsCountsByTypeStatusCategory() throws Exception {
        String token = registerStudentAndGetToken("it22@test.com", "IT022");

        long lostBefore = statValue(token, "/data/byType/LOST");
        long resolvedBefore = statValue(token, "/data/byStatus/RESOLVED");
        long openBefore = statValue(token, "/data/byStatus/OPEN");
        long totalBefore = statValue(token, "/data/totalItems");

        reportLost(token, "Stats item one", CAMPUS_LAT, CAMPUS_LNG);
        Long second = reportLost(token, "Stats item two", CAMPUS_LAT, CAMPUS_LNG);
        changeItemStatus(token, second, LostFoundItemStatus.RESOLVED);

        assertThat(statValue(token, "/data/byType/LOST") - lostBefore).isEqualTo(2);
        assertThat(statValue(token, "/data/byStatus/RESOLVED") - resolvedBefore).isEqualTo(1);
        assertThat(statValue(token, "/data/byStatus/OPEN") - openBefore).isEqualTo(1);
        assertThat(statValue(token, "/data/totalItems") - totalBefore).isEqualTo(2);
    }

    @Test
    void stats_bucketsAreZeroFilledForEveryEnumConstant() throws Exception {
        String token = registerStudentAndGetToken("it23@test.com", "IT023");

        // Every constant is present as a key, so a dashboard never has to null-check a bucket.
        mockMvc.perform(get(BASE + "/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.byType.LOST").exists())
                .andExpect(jsonPath("$.data.byType.FOUND").exists())
                .andExpect(jsonPath("$.data.byStatus.OPEN").exists())
                .andExpect(jsonPath("$.data.byStatus.CLAIMED").exists())
                .andExpect(jsonPath("$.data.byStatus.RESOLVED").exists())
                .andExpect(jsonPath("$.data.byStatus.EXPIRED").exists())
                .andExpect(jsonPath("$.data.byStatus.CANCELLED").exists())
                .andExpect(jsonPath("$.data.byCategory.PETS").exists())
                .andExpect(jsonPath("$.data.byCategory.CARDS_AND_KEYS").exists());
    }

    private long statValue(String token, String pointer) throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at(pointer).asLong();
    }

    private CreateLostFoundItemRequest lost(String title, BigDecimal lat, BigDecimal lng) {
        return new CreateLostFoundItemRequest(
                LostFoundItemType.LOST, LostFoundCategory.ELECTRONICS, title, "desc", null, null,
                "Test incident place", lat, lng, null, null, null, null,
                LocalDateTime.now().minusDays(1), null);
    }

    private UpdateLostFoundItemRequest emptyUpdate(String title) {
        return new UpdateLostFoundItemRequest(
                null, title, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
