package com.unisphere.backend.campus.lostfound.seeder;

import com.unisphere.backend.campus.lostfound.entity.LostFoundClaim;
import com.unisphere.backend.campus.lostfound.entity.LostFoundItem;
import com.unisphere.backend.campus.lostfound.entity.LostFoundItemMedia;
import com.unisphere.backend.campus.lostfound.enums.*;
import com.unisphere.backend.campus.lostfound.repository.LostFoundClaimRepository;
import com.unisphere.backend.campus.lostfound.repository.LostFoundItemRepository;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Seeds sample lost &amp; found reports and claims. Runs after CommunitySeeder (@Order(5)).
 * Guard: skips entirely if any item already exists.
 *
 * <p>Covers every state the module can be in, and two scenarios the frontend needs live data for:
 * a high-scoring matcher pair, and a FOUND item whose masked view differs visibly between the
 * reporter and everyone else.
 *
 * <p>Coordinates cluster around ~3.0678, 101.5006, consistent with the UiTM references in
 * {@code NewsSeeder}.
 */
@Slf4j
@Component
@Order(6)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeding.enabled", havingValue = "true", matchIfMissing = false)
public class LostFoundSeeder implements CommandLineRunner {

    private static final BigDecimal CAMPUS_LAT = new BigDecimal("3.0678000");
    private static final BigDecimal CAMPUS_LNG = new BigDecimal("101.5006000");

    private final UserRepository userRepository;
    private final LostFoundItemRepository itemRepository;
    private final LostFoundClaimRepository claimRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (itemRepository.count() > 0) {
            log.info("Lost & found items already seeded — skipping.");
            return;
        }

        Optional<User> studentOpt  = userRepository.findByEmail("student@unisphere.dev");
        Optional<User> alumniOpt   = userRepository.findByEmail("alumni@unisphere.dev");
        Optional<User> clubOpt     = userRepository.findByEmail("club@unisphere.dev");
        Optional<User> employerOpt = userRepository.findByEmail("employer@unisphere.dev");

        if (studentOpt.isEmpty() || alumniOpt.isEmpty() || clubOpt.isEmpty() || employerOpt.isEmpty()) {
            log.warn("LostFoundSeeder: seeded users not found — run UserSeeder first (app.seeding.enabled=true).");
            return;
        }

        User student  = studentOpt.get();
        User alumni   = alumniOpt.get();
        User club     = clubOpt.get();
        User employer = employerOpt.get();

        LocalDateTime now = LocalDateTime.now();

        // ── The matcher demo pair ────────────────────────────────────────────
        // Same category, ~200 m apart, two days apart, different reporters — so
        // GET /items/{lostPowerBank.id}/matches returns a high-scoring hit immediately.
        LostFoundItem lostPowerBank = item(student, LostFoundItemType.LOST, LostFoundCategory.ELECTRONICS,
                "Black Anker power bank, 20000mAh",
                "Slim black Anker power bank with a small dent on the corner. Lost somewhere between "
                        + "the library and the lecture halls.",
                "Engraved initials on the underside",
                "Library Level 3, near the printers",
                CAMPUS_LAT, CAMPUS_LNG,
                "Faculty Office, Level 1", CAMPUS_LAT, CAMPUS_LNG,
                "Ask for the front desk during office hours",
                now.minusDays(5));

        LostFoundItem foundPowerBank = item(alumni, LostFoundItemType.FOUND, LostFoundCategory.ELECTRONICS,
                "Black power bank found near DKP2",
                "Found a black power bank on a bench outside DKP2. Charging port looks worn.",
                "Has a dent on one corner",
                "Bench outside DKP2",
                new BigDecimal("3.0695000"), new BigDecimal("101.5013000"),
                "Security Post B", new BigDecimal("3.0688000"), new BigDecimal("101.5009000"),
                "Bring your student ID to collect",
                now.minusDays(3));

        // ── The privacy demo ─────────────────────────────────────────────────
        // Logged in as student@unisphere.dev (not the reporter) this shows a coarsened pin and a
        // null pickup; the reporter sees the exact coordinates, the pickup point and the secret.
        LostFoundItem foundMatricCard = item(club, LostFoundItemType.FOUND, LostFoundCategory.CARDS_AND_KEYS,
                "Matric card found in the cafeteria",
                "Found a student matric card under a table in the main cafeteria.",
                "The name and matric number on the card",
                "Main cafeteria, table 12",
                new BigDecimal("3.0671000"), new BigDecimal("101.4998000"),
                "Student Affairs counter", new BigDecimal("3.0670000"), new BigDecimal("101.4995000"),
                "Open weekdays 9am-5pm, ask for Lost & Found",
                now.minusDays(1));

        // ── An OPEN item with two pending claims ─────────────────────────────
        LostFoundItem foundHeadphones = item(alumni, LostFoundItemType.FOUND, LostFoundCategory.ELECTRONICS,
                "Wireless headphones left in Lab 4",
                "Over-ear wireless headphones, black with a white cable, left on a desk in Lab 4.",
                "The brand and what is in the case",
                "Computer Lab 4",
                new BigDecimal("3.0702000"), new BigDecimal("101.5021000"),
                "Lab 4 technician's desk", new BigDecimal("3.0702000"), new BigDecimal("101.5021000"),
                "Weekdays only, before 6pm",
                now.minusDays(2));

        // ── A CLAIMED item, one approved claim plus two auto-rejected siblings ─
        LostFoundItem foundWallet = item(club, LostFoundItemType.FOUND, LostFoundCategory.BAGS,
                "Brown leather wallet found at the bus stop",
                "Brown leather wallet found at the main gate bus stop. Contents intact.",
                "The exact cards inside",
                "Main gate bus stop",
                new BigDecimal("3.0655000"), new BigDecimal("101.4981000"),
                "Security Post A", new BigDecimal("3.0656000"), new BigDecimal("101.4982000"),
                "24-hour desk, bring ID",
                now.minusDays(9));
        foundWallet.setStatus(LostFoundItemStatus.CLAIMED);

        // ── Terminal and edge states ─────────────────────────────────────────
        LostFoundItem resolvedKeys = item(student, LostFoundItemType.LOST, LostFoundCategory.CARDS_AND_KEYS,
                "Bunch of keys with a blue lanyard",
                "Three keys on a blue lanyard. Already recovered — thank you!",
                null,
                "Sports complex changing room",
                new BigDecimal("3.0710000"), new BigDecimal("101.5030000"),
                "Return to me at the sports complex", null, null, null,
                now.minusDays(20));
        resolvedKeys.setStatus(LostFoundItemStatus.RESOLVED);
        resolvedKeys.setResolvedAt(now.minusDays(18));

        LostFoundItem cancelledBottle = item(student, LostFoundItemType.LOST, LostFoundCategory.OTHER,
                "Steel water bottle",
                "Dark green insulated bottle. Gave up looking.",
                null,
                "Somewhere on the north campus",
                new BigDecimal("3.0721000"), new BigDecimal("101.5040000"),
                null, null, null, null,
                now.minusDays(30));
        cancelledBottle.setStatus(LostFoundItemStatus.CANCELLED);

        // Status set directly rather than by backdating: created_at is @CreatedDate updatable=false,
        // so a seeder cannot age a row. The row reads as reported today, which is cosmetic.
        LostFoundItem expiredUmbrella = item(alumni, LostFoundItemType.FOUND, LostFoundCategory.ACCESSORIES,
                "Umbrella left in the atrium",
                "Plain black folding umbrella, nobody claimed it.",
                null,
                "Main building atrium",
                new BigDecimal("3.0680000"), new BigDecimal("101.5001000"),
                "Reception desk", new BigDecimal("3.0680000"), new BigDecimal("101.5001000"), null,
                now.minusDays(90));
        expiredUmbrella.setStatus(LostFoundItemStatus.EXPIRED);

        // ── Two reports with no coordinates at all ───────────────────────────
        // So the frontend's "no location" rendering path has data to exercise.
        LostFoundItem lostGlasses = item(student, LostFoundItemType.LOST, LostFoundCategory.ACCESSORIES,
                "Prescription glasses in a grey case",
                "Thin wire frames in a soft grey case. Not sure where I put them down.",
                "The optician's name printed inside the case",
                "Somewhere on campus — not sure",
                null, null, "Return to me via the faculty office", null, null, null,
                now.minusDays(4));

        LostFoundItem lostNotebook = item(employer, LostFoundItemType.LOST, LostFoundCategory.BOOKS,
                "A5 hardcover notebook, navy",
                "Navy hardcover notebook with interview notes. No name inside.",
                null,
                null,
                null, null, null, null, null, null,
                now.minusDays(6));

        itemRepository.saveAll(java.util.List.of(
                lostPowerBank, foundPowerBank, foundMatricCard, foundHeadphones, foundWallet,
                resolvedKeys, cancelledBottle, expiredUmbrella, lostGlasses, lostNotebook));

        // ── Claims ───────────────────────────────────────────────────────────
        claimRepository.saveAll(java.util.List.of(
                // Two live claims on the headphones, so the reporter's queue and pendingClaimCount
                // are both non-zero out of the box.
                claim(foundHeadphones, student, LostFoundClaimStatus.PENDING,
                        "They are mine — black Sony over-ears, and the case has a sticker of a red fox on it.",
                        null, null),
                claim(foundHeadphones, employer, LostFoundClaimStatus.PENDING,
                        "I left a pair of headphones in that lab last Tuesday afternoon after the session.",
                        null, null),

                // The wallet: one approved claim and the two siblings it auto-rejected.
                claim(foundWallet, student, LostFoundClaimStatus.APPROVED,
                        "Brown leather, my library card and a blue bank card are inside, plus a photo.",
                        club, "Details match, collected in person"),
                claim(foundWallet, alumni, LostFoundClaimStatus.REJECTED,
                        "I think that might be the wallet I lost near the gate a couple of weeks ago.",
                        club, "Another claim was approved"),
                claim(foundWallet, employer, LostFoundClaimStatus.REJECTED,
                        "Lost a brown wallet recently, could well be that one at the bus stop.",
                        club, "Another claim was approved"),

                // A claimant who withdrew — reviewedBy stays null, cancelling is not a review.
                claim(foundMatricCard, employer, LostFoundClaimStatus.CANCELLED,
                        "I lost my card last week, this could be it — actually found mine, sorry!",
                        null, null)));

        log.info("Seeded 10 lost & found items and 6 claims.");
    }

    @SuppressWarnings("java:S107") // A seeder fixture builder; named constants would be less readable.
    private LostFoundItem item(User reporter, LostFoundItemType type, LostFoundCategory category,
                               String title, String description, String identifyingDetail,
                               String incidentPlace, BigDecimal incidentLat, BigDecimal incidentLng,
                               String pickupPlace, BigDecimal pickupLat, BigDecimal pickupLng,
                               String pickupInstructions, LocalDateTime occurredAt) {
        LostFoundItem item = new LostFoundItem();
        item.setReportedBy(reporter.getId());
        item.setUniversityId(universityIdOf(reporter));
        item.setItemType(type);
        item.setCategory(category);
        item.setStatus(LostFoundItemStatus.OPEN);
        item.setTitle(title);
        item.setDescription(description);
        item.setIdentifyingDetail(identifyingDetail);
        item.setIncidentPlace(incidentPlace);
        item.setIncidentLatitude(incidentLat);
        item.setIncidentLongitude(incidentLng);
        item.setPickupPlace(pickupPlace);
        item.setPickupLatitude(pickupLat);
        item.setPickupLongitude(pickupLng);
        item.setPickupInstructions(pickupInstructions);
        item.setOccurredAt(occurredAt);

        // Bare object keys, never resolved URLs — see changeset 012. These point at objects that do
        // not exist in dev storage; MediaUrlResolver still signs them, and the browser 404s on the
        // image, which is the intended "no real asset" behaviour for seed data.
        item.setPrimaryImageKey("lost-found/" + reporter.getId() + "/seed-primary.jpg");
        item.getMedia().add(media(item, "lost-found/" + reporter.getId() + "/seed-gallery-1.jpg", 0));
        item.getMedia().add(media(item, "lost-found/" + reporter.getId() + "/seed-gallery-2.jpg", 1));
        return item;
    }

    private LostFoundItemMedia media(LostFoundItem item, String key, int sortOrder) {
        LostFoundItemMedia media = new LostFoundItemMedia();
        media.setItem(item);
        media.setMediaKey(key);
        media.setMediaType(LostFoundMediaType.IMAGE);
        media.setSortOrder(sortOrder);
        return media;
    }

    private LostFoundClaim claim(LostFoundItem item, User claimant, LostFoundClaimStatus status,
                                 String proofText, User reviewer, String decisionNote) {
        LostFoundClaim claim = new LostFoundClaim();
        claim.setItemId(item.getId());
        claim.setClaimantId(claimant.getId());
        claim.setStatus(status);
        claim.setProofText(proofText);
        claim.setDecisionNote(decisionNote);
        if (reviewer != null) {
            claim.setReviewedBy(reviewer.getId());
            claim.setReviewedAt(LocalDateTime.now());
        }
        return claim;
    }

    /**
     * Mirrors LostFoundAccessService.viewerUniversityId without importing it — the seeder builds
     * entities directly and deliberately bypasses the service layer, as every other seeder does.
     */
    private Long universityIdOf(User user) {
        if (user instanceof com.unisphere.backend.identity.entity.Student s)    return s.getUniversityId();
        if (user instanceof com.unisphere.backend.identity.entity.Alumni a)     return a.getUniversityId();
        if (user instanceof com.unisphere.backend.identity.entity.Club c)       return c.getUniversityId();
        if (user instanceof com.unisphere.backend.identity.entity.University u) return u.getId();
        return null;
    }
}
