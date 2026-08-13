package com.unisphere.backend.campus.lostfound;

import com.unisphere.backend.campus.lostfound.entity.LostFoundItem;
import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemStatus;
import com.unisphere.backend.campus.lostfound.enums.LostFoundItemType;
import com.unisphere.backend.campus.lostfound.repository.LostFoundItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bulk expiry sweep.
 *
 * <p>Rows are aged by passing a <em>future</em> cutoff rather than by backdating {@code createdAt},
 * which is {@code @CreatedDate updatable = false} and cannot be set by a test. That is exactly why
 * {@code cutoff} is a query parameter rather than being computed inside the statement.
 *
 * <p>Unlike every other class here these tests assert on the sweep's <em>returned row count</em>,
 * which is a whole-table number. The MySQL container is shared across the entire run with no
 * rollback between tests, so the table is truncated first — otherwise every leftover OPEN item from
 * another test class lands in the count.
 */
class LostFoundExpirySchedulerTest extends AbstractLostFoundIntegrationTest {

    @Autowired
    private LostFoundItemRepository itemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearLostFoundTables() {
        // Raw SQL rather than repository.deleteAll(): @SQLRestriction hides soft-deleted rows from
        // findAll(), so deleteAll() would silently leave them behind — and one of these tests
        // depends on a soft-deleted row being the only thing in the table.
        jdbcTemplate.execute("DELETE FROM lost_found_claims");
        jdbcTemplate.execute("DELETE FROM lost_found_item_media");
        jdbcTemplate.execute("DELETE FROM lost_found_items");
    }

    @Test
    void openItemOlderThanCutoffIsExpired() throws Exception {
        String token = registerStudentAndGetToken("exp1@test.com", "EXP001");
        Long itemId = reportLost(token, "Stale item", CAMPUS_LAT, CAMPUS_LNG);

        int expired = sweep(LocalDateTime.now().plusMinutes(5));

        assertEquals(1, expired);
        assertEquals(LostFoundItemStatus.EXPIRED, itemRepository.findById(itemId).orElseThrow().getStatus());
    }

    @Test
    void aSecondSweepExpiresNothing() throws Exception {
        String token = registerStudentAndGetToken("exp2@test.com", "EXP002");
        reportLost(token, "Stale item", CAMPUS_LAT, CAMPUS_LNG);

        LocalDateTime cutoff = LocalDateTime.now().plusMinutes(5);
        assertEquals(1, sweep(cutoff));

        // The status = OPEN guard is what makes the job safe on every Render replica with no
        // distributed lock: a second instance ticking the same second matches zero rows.
        assertEquals(0, sweep(cutoff));
    }

    @Test
    void recentOpenItemIsLeftAlone() throws Exception {
        String token = registerStudentAndGetToken("exp3@test.com", "EXP003");
        Long itemId = reportLost(token, "Fresh item", CAMPUS_LAT, CAMPUS_LNG);

        int expired = sweep(LocalDateTime.now().minusDays(60));

        assertEquals(0, expired);
        assertEquals(LostFoundItemStatus.OPEN, itemRepository.findById(itemId).orElseThrow().getStatus());
    }

    @Test
    void resolvedItemIsNotExpired() throws Exception {
        String token = registerStudentAndGetToken("exp4@test.com", "EXP004");
        Long itemId = reportLost(token, "Already resolved", CAMPUS_LAT, CAMPUS_LNG);
        changeItemStatus(token, itemId, LostFoundItemStatus.RESOLVED);

        assertEquals(0, sweep(LocalDateTime.now().plusMinutes(5)));
        assertEquals(LostFoundItemStatus.RESOLVED, itemRepository.findById(itemId).orElseThrow().getStatus());
    }

    @Test
    void softDeletedItemIsNotResurrected() throws Exception {
        String token = registerStudentAndGetToken("exp5@test.com", "EXP005");
        Long reporterId = getUserId(token);

        LostFoundItem deleted = transactionTemplate.execute(tx -> {
            LostFoundItem item = new LostFoundItem();
            item.setReportedBy(reporterId);
            item.setItemType(LostFoundItemType.LOST);
            item.setCategory(LostFoundCategory.OTHER);
            item.setStatus(LostFoundItemStatus.OPEN);
            item.setTitle("Soft-deleted stale item");
            item.setOccurredAt(LocalDateTime.now().minusDays(1));
            item.setDeletedAt(LocalDateTime.now());
            return itemRepository.save(item);
        });

        // @SQLRestriction is NOT applied to bulk HQL updates, so the query carries
        // deletedAt IS NULL by hand — without it this row would resurrect itself as EXPIRED.
        assertEquals(0, sweep(LocalDateTime.now().plusMinutes(5)));

        // Read raw: @SQLRestriction hides soft-deleted rows from findById too, so the repository
        // cannot see the very row under test. The status must still be OPEN — the sweep skipped it.
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM lost_found_items WHERE id = ?", String.class, deleted.getId());
        assertEquals(LostFoundItemStatus.OPEN.name(), status);
    }

    @Test
    void updatedAtAdvancesOnBulkExpire() throws Exception {
        String token = registerStudentAndGetToken("exp6@test.com", "EXP006");
        Long itemId = reportLost(token, "Stale item", CAMPUS_LAT, CAMPUS_LNG);
        LocalDateTime before = itemRepository.findById(itemId).orElseThrow().getUpdatedAt();

        LocalDateTime now = LocalDateTime.now().plusHours(1);
        transactionTemplate.execute(tx ->
                itemRepository.expireStaleItems(LocalDateTime.now().plusMinutes(5), now));

        // JPA auditing does not fire on bulk updates, so updatedAt is set explicitly by the query.
        LocalDateTime after = itemRepository.findById(itemId).orElseThrow().getUpdatedAt();
        assertTrue(after.isAfter(before), "updatedAt should advance on a bulk expire");
    }

    private int sweep(LocalDateTime cutoff) {
        Integer count = transactionTemplate.execute(tx ->
                itemRepository.expireStaleItems(cutoff, LocalDateTime.now()));
        return count == null ? 0 : count;
    }
}
