package com.unisphere.backend.campus.lostfound.service;

import com.unisphere.backend.campus.lostfound.dto.response.LostFoundItemSummaryResponse;
import com.unisphere.backend.campus.lostfound.dto.response.LostFoundMatchResponse;
import com.unisphere.backend.campus.lostfound.entity.LostFoundItem;
import com.unisphere.backend.campus.lostfound.enums.LostFoundCategory;
import com.unisphere.backend.campus.lostfound.repository.LostFoundItemRepository;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Suggests counterpart reports — FOUND candidates for a LOST report and vice versa.
 *
 * <p>Read-only and additive: no extra tables, no denormalised scores. SQL narrows the field
 * (counterpart type, still open, not your own, inside the date window, roughly the right place) and
 * pre-ranks by FULLTEXT relevance so the {@code LIMIT} cuts the right rows; Java then scores the
 * survivors on four weighted terms and keeps the best few.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class LostFoundMatchService {

    // Weights sum to 100. Declared here rather than in properties: they are a scoring model, not an
    // operational knob, and changing one without re-reading the others produces nonsense.
    private static final int WEIGHT_CATEGORY = 30;
    private static final int WEIGHT_GEO = 25;
    private static final int WEIGHT_TEXT = 25;
    private static final int WEIGHT_DATE = 20;

    /** Score awarded on the geo term when either side has no coordinates — present, but discounted. */
    private static final double GEO_UNKNOWN_FACTOR = 0.3;

    /** Score awarded on the category term when either side is OTHER — a non-answer, not a mismatch. */
    private static final double CATEGORY_OTHER_FACTOR = 0.5;

    private static final double KM_PER_DEGREE = 111.045;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    /**
     * Words that carry no discriminating signal in a lost-and-found title. Kept deliberately small —
     * an aggressive list would strip "black" or "small", which are exactly the words that match.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "his", "her", "its", "was", "are", "near", "lost", "found",
            "some", "any", "one", "this", "that", "have", "has", "had", "from", "about", "please");

    private final LostFoundItemRepository itemRepository;
    private final LostFoundAccessService accessService;
    private final LostFoundService lostFoundService;

    @Value("${lost-found.match.window-days:30}")
    private int windowDays;

    @Value("${lost-found.match.radius-km:10}")
    private double matchRadiusKm;

    @Value("${lost-found.match.candidate-limit:200}")
    private int candidateLimit;

    @Value("${lost-found.match.max-results:10}")
    private int maxResults;

    /**
     * Scored counterpart suggestions for one report.
     *
     * <p>Restricted to the reporter and admins. A list of FOUND items that plausibly match a
     * stranger's missing wallet is a ready-made shortlist for a fraudulent claim, so this is not a
     * public read even though every individual item on it is.
     */
    public List<LostFoundMatchResponse> findMatches(Long itemId, User currentUser) {
        LostFoundItem source = lostFoundService.findActiveItem(itemId);
        if (!accessService.isOwnerOrAdmin(source, currentUser)) {
            throw new UnauthorizedActionException("You can only view matches for your own reports");
        }

        LocalDateTime from = source.getOccurredAt().minusDays(windowDays);
        LocalDateTime to = source.getOccurredAt().plusDays(windowDays);

        BigDecimal minLat = null;
        BigDecimal maxLat = null;
        BigDecimal minLng = null;
        BigDecimal maxLng = null;
        if (source.getIncidentLatitude() != null && source.getIncidentLongitude() != null) {
            double latDelta = matchRadiusKm / KM_PER_DEGREE;
            double lngDelta = matchRadiusKm / (KM_PER_DEGREE * Math.max(
                    Math.cos(Math.toRadians(source.getIncidentLatitude().doubleValue())), 0.01));
            minLat = source.getIncidentLatitude().subtract(BigDecimal.valueOf(latDelta));
            maxLat = source.getIncidentLatitude().add(BigDecimal.valueOf(latDelta));
            minLng = source.getIncidentLongitude().subtract(BigDecimal.valueOf(lngDelta));
            maxLng = source.getIncidentLongitude().add(BigDecimal.valueOf(lngDelta));
        }

        List<LostFoundItem> candidates = itemRepository.findMatchCandidates(
                source.getItemType().counterpart().name(),
                source.getReportedBy(),
                from, to,
                source.getUniversityId(),
                minLat, maxLat, minLng, maxLng,
                searchText(source),
                PageRequest.of(0, candidateLimit));

        if (candidates.isEmpty()) return List.of();

        Set<String> sourceTokens = tokenize(source);

        List<Scored> scored = candidates.stream()
                .map(candidate -> score(source, candidate, sourceTokens))
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(maxResults)
                .toList();

        // Mapping goes through LostFoundService so the privacy mask applies to suggestions too —
        // this service deliberately never builds an item DTO itself.
        List<LostFoundItemSummaryResponse> summaries = lostFoundService.toSummaryResponses(
                scored.stream().map(Scored::item).toList(), currentUser);

        List<LostFoundMatchResponse> results = new ArrayList<>(scored.size());
        for (int i = 0; i < scored.size(); i++) {
            results.add(new LostFoundMatchResponse(summaries.get(i), scored.get(i).score(), scored.get(i).reasons()));
        }
        return results;
    }

    private record Scored(LostFoundItem item, int score, List<String> reasons) {}

    private Scored score(LostFoundItem source, LostFoundItem candidate, Set<String> sourceTokens) {
        List<String> reasons = new ArrayList<>(4);
        double total = 0;

        // ── Category ──
        double categoryFactor;
        if (source.getCategory() == candidate.getCategory()) {
            categoryFactor = 1.0;
            reasons.add("Same category (" + candidate.getCategory() + ")");
        } else if (source.getCategory() == LostFoundCategory.OTHER
                || candidate.getCategory() == LostFoundCategory.OTHER) {
            categoryFactor = CATEGORY_OTHER_FACTOR;
        } else {
            categoryFactor = 0.0;
        }
        total += WEIGHT_CATEGORY * categoryFactor;

        // ── Distance ──
        Double distanceKm = distanceKm(source, candidate);
        if (distanceKm == null) {
            total += WEIGHT_GEO * GEO_UNKNOWN_FACTOR;
        } else {
            total += WEIGHT_GEO * (1 - Math.min(distanceKm / matchRadiusKm, 1.0));
            reasons.add(formatDistance(distanceKm));
        }

        // ── Date proximity ──
        long daysApart = Math.abs(Duration.between(source.getOccurredAt(), candidate.getOccurredAt()).toDays());
        total += WEIGHT_DATE * (1 - Math.min((double) daysApart, windowDays) / windowDays);
        reasons.add(daysApart == 0 ? "Reported the same day" : "Reported " + daysApart + " day(s) apart");

        // ── Text overlap ──
        // Jaccard in Java rather than a second MATCH ... AGAINST round trip: MySQL's relevance
        // score would need a projection or an @SqlResultSetMapping. The >= 3 character rule mirrors
        // innodb_ft_min_token_size's default so the SQL pre-rank and this stage agree on what a
        // word is.
        Set<String> candidateTokens = tokenize(candidate);
        Set<String> shared = new LinkedHashSet<>(sourceTokens);
        shared.retainAll(candidateTokens);
        Set<String> union = new HashSet<>(sourceTokens);
        union.addAll(candidateTokens);
        double jaccard = union.isEmpty() ? 0 : (double) shared.size() / union.size();
        total += WEIGHT_TEXT * jaccard;
        if (!shared.isEmpty()) {
            reasons.add("Shares: " + shared.stream().limit(5).collect(Collectors.joining(", ")));
        }

        return new Scored(candidate, (int) Math.round(total), reasons);
    }

    private Double distanceKm(LostFoundItem a, LostFoundItem b) {
        if (a.getIncidentLatitude() == null || a.getIncidentLongitude() == null
                || b.getIncidentLatitude() == null || b.getIncidentLongitude() == null) {
            return null;
        }
        double lat1 = a.getIncidentLatitude().doubleValue();
        double lng1 = a.getIncidentLongitude().doubleValue();
        double lat2 = b.getIncidentLatitude().doubleValue();
        double lng2 = b.getIncidentLongitude().doubleValue();
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLng / 2), 2);
        return EARTH_RADIUS_KM * 2 * Math.asin(Math.sqrt(h));
    }

    private String formatDistance(double km) {
        return km < 1 ? Math.round(km * 1000) + " m away" : String.format("%.1f km away", km);
    }

    private Set<String> tokenize(LostFoundItem item) {
        String text = (item.getTitle() == null ? "" : item.getTitle()) + " "
                + (item.getDescription() == null ? "" : item.getDescription());
        return Arrays.stream(TOKEN_SPLIT.split(text.toLowerCase()))
                .filter(t -> t.length() >= 3)
                .filter(t -> !STOP_WORDS.contains(t))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * The relevance probe fed to MySQL. NATURAL LANGUAGE MODE tolerates raw punctuation, so the
     * reporter's own title needs no operator escaping — but an empty probe would make MySQL rank
     * every row equally, so fall back to the title.
     */
    private String searchText(LostFoundItem source) {
        String text = String.join(" ", tokenize(source));
        return text.isBlank() ? source.getTitle() : text;
    }
}
