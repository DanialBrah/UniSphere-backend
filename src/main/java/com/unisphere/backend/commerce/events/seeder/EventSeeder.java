package com.unisphere.backend.commerce.events.seeder;

import com.unisphere.backend.commerce.events.entity.Event;
import com.unisphere.backend.commerce.events.entity.EventRegistration;
import com.unisphere.backend.commerce.events.enums.EventCategory;
import com.unisphere.backend.commerce.events.enums.EventRegistrationMode;
import com.unisphere.backend.commerce.events.enums.EventRegistrationStatus;
import com.unisphere.backend.commerce.events.enums.EventStatus;
import com.unisphere.backend.commerce.events.repository.EventRegistrationRepository;
import com.unisphere.backend.commerce.events.repository.EventRepository;
import com.unisphere.backend.identity.entity.Alumni;
import com.unisphere.backend.identity.entity.Club;
import com.unisphere.backend.identity.entity.Student;
import com.unisphere.backend.identity.entity.University;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Seeds sample events and registrations. Runs after LostFoundSeeder (@Order(6)). Guard: skips
 * entirely if any event already exists.
 *
 * <p>Covers every {@link EventStatus}, both registration modes, an event near capacity with an
 * active waitlist, and a completed event with a checked-in attendee — every state the frontend
 * needs live data for.
 *
 * <p>Coordinates cluster around ~3.0678, 101.5006, consistent with the UiTM references in
 * {@code NewsSeeder}/{@code LostFoundSeeder}.
 */
@Slf4j
@Component
@Order(7)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeding.enabled", havingValue = "true", matchIfMissing = false)
public class EventSeeder implements CommandLineRunner {

    private static final BigDecimal CAMPUS_LAT = new BigDecimal("3.0678000");
    private static final BigDecimal CAMPUS_LNG = new BigDecimal("101.5006000");

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final EventRegistrationRepository eventRegistrationRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (eventRepository.count() > 0) {
            log.info("Events already seeded — skipping.");
            return;
        }

        Optional<User> studentOpt    = userRepository.findByEmail("student@unisphere.dev");
        Optional<User> alumniOpt     = userRepository.findByEmail("alumni@unisphere.dev");
        Optional<User> clubOpt       = userRepository.findByEmail("club@unisphere.dev");
        Optional<User> employerOpt   = userRepository.findByEmail("employer@unisphere.dev");
        Optional<User> universityOpt = userRepository.findByEmail("university@unisphere.dev");

        if (studentOpt.isEmpty() || alumniOpt.isEmpty() || clubOpt.isEmpty()
                || employerOpt.isEmpty() || universityOpt.isEmpty()) {
            log.warn("EventSeeder: seeded users not found — run UserSeeder first (app.seeding.enabled=true).");
            return;
        }

        User student    = studentOpt.get();
        User alumni     = alumniOpt.get();
        User club       = clubOpt.get();
        User employer   = employerOpt.get();
        User university = universityOpt.get();

        LocalDateTime now = LocalDateTime.now();

        // ── DRAFT — not yet published, visible only to its organizer ────────────
        Event draftStudyGroup = physicalEvent(student, EventCategory.ACADEMIC,
                "Finals Study Group Kickoff", "Planning session for the finals-week study group.",
                now.plusDays(10), now.plusDays(10).plusHours(2),
                "Library Level 2, Discussion Room 3", CAMPUS_LAT, CAMPUS_LNG, 20);
        draftStudyGroup.setStatus(EventStatus.DRAFT);

        // ── PUBLISHED, physical, near capacity — the waitlist demo ──────────────
        // maxCapacity=2 with 2 REGISTERED + 1 WAITLISTED, so GET /events/{id} shows availableSeats=0
        // and the waitlist promotion path has a live candidate to exercise.
        Event techTalk = physicalEvent(club, EventCategory.TECH,
                "Tech Talk: Building with AI", "A student-club session on practical AI tooling for coursework.",
                now.plusDays(7), now.plusDays(7).plusHours(2),
                "Faculty of Computing, Auditorium A", new BigDecimal("3.0695000"), new BigDecimal("101.5013000"), 2);
        techTalk.setStatus(EventStatus.PUBLISHED);

        // ── PUBLISHED, online, unlimited capacity ────────────────────────────────
        Event careerTalk = onlineEvent(university, EventCategory.CAREER,
                "Virtual Career Readiness Talk", "An online session on interview preparation and resume reviews.",
                now.plusDays(3), now.plusDays(3).plusHours(1), "https://meet.unisphere.dev/career-readiness");
        careerTalk.setStatus(EventStatus.PUBLISHED);

        // ── PUBLISHED, external registration — the "link to another site" flow ──
        Event careerFair = physicalEvent(employer, EventCategory.CAREER,
                "Annual Campus Career Fair", "Meet recruiters from over 30 companies. Registration is on our partner platform.",
                now.plusDays(14), now.plusDays(14).plusHours(6),
                "Main Sports Complex", new BigDecimal("3.0710000"), new BigDecimal("101.5030000"), null);
        careerFair.setStatus(EventStatus.PUBLISHED);
        careerFair.setRegistrationMode(EventRegistrationMode.EXTERNAL);
        careerFair.setExternalRegistrationUrl("https://careerfair.example.com/register");

        // ── CANCELLED ─────────────────────────────────────────────────────────
        Event charityRun = physicalEvent(club, EventCategory.SPORTS,
                "Campus Charity Run", "5km fun run in support of the local children's shelter.",
                now.plusDays(5), now.plusDays(5).plusHours(3),
                "Main Gate assembly point", new BigDecimal("3.0655000"), new BigDecimal("101.4981000"), 100);
        charityRun.setStatus(EventStatus.CANCELLED);

        // ── COMPLETED — dates in the past, plus a checked-in attendee ────────────
        // Status set directly rather than waiting on EventCompletionScheduler: start/end are plain
        // fields (not @CreatedDate-controlled), so backdating them here is fine, unlike createdAt.
        Event homecoming = physicalEvent(alumni, EventCategory.SOCIAL,
                "Alumni Homecoming Mixer", "An evening of reconnecting with fellow graduates.",
                now.minusDays(20), now.minusDays(20).plusHours(3),
                "Alumni Hall", new BigDecimal("3.0688000"), new BigDecimal("101.5009000"), 50);
        homecoming.setStatus(EventStatus.COMPLETED);

        eventRepository.saveAll(List.of(
                draftStudyGroup, techTalk, careerTalk, careerFair, charityRun, homecoming));

        // ── Registrations ────────────────────────────────────────────────────────
        techTalk.setRegisteredCount(2);
        techTalk.setWaitlistedCount(1);
        careerTalk.setRegisteredCount(1);
        homecoming.setRegisteredCount(1);
        eventRepository.saveAll(List.of(techTalk, careerTalk, homecoming));

        eventRegistrationRepository.saveAll(List.of(
                registration(techTalk, student, EventRegistrationStatus.REGISTERED, null, null),
                registration(techTalk, alumni, EventRegistrationStatus.REGISTERED, null, null),
                registration(techTalk, employer, EventRegistrationStatus.WAITLISTED, null, null),

                registration(careerTalk, student, EventRegistrationStatus.REGISTERED, null, null),

                // Checked in by the organizer (alumni) at the door.
                registration(homecoming, student, EventRegistrationStatus.ATTENDED,
                        now.minusDays(20).plusHours(1), alumni.getId()),
                registration(homecoming, club, EventRegistrationStatus.CANCELLED, null, null)));

        log.info("Seeded 6 events and 6 registrations.");
    }

    @SuppressWarnings("java:S107") // A seeder fixture builder; named constants would be less readable.
    private Event physicalEvent(User organizer, EventCategory category, String title, String description,
                                LocalDateTime start, LocalDateTime end, String venueName,
                                BigDecimal latitude, BigDecimal longitude, Integer maxCapacity) {
        Event event = base(organizer, category, title, description, start, end, maxCapacity);
        event.setOnline(false);
        event.setVenueName(venueName);
        event.setLatitude(latitude);
        event.setLongitude(longitude);
        return event;
    }

    private Event onlineEvent(User organizer, EventCategory category, String title, String description,
                              LocalDateTime start, LocalDateTime end, String onlineUrl) {
        Event event = base(organizer, category, title, description, start, end, null);
        event.setOnline(true);
        event.setOnlineUrl(onlineUrl);
        return event;
    }

    private Event base(User organizer, EventCategory category, String title, String description,
                       LocalDateTime start, LocalDateTime end, Integer maxCapacity) {
        Event event = new Event();
        event.setOrganizerId(organizer.getId());
        event.setUniversityId(universityIdOf(organizer));
        event.setCategory(category);
        event.setTitle(title);
        event.setDescription(description);
        event.setStartDatetime(start);
        event.setEndDatetime(end);
        event.setRegistrationMode(EventRegistrationMode.INTERNAL);
        event.setMaxCapacity(maxCapacity);
        // Bare object key, never a resolved URL — see changeset 012. Points at an object that does
        // not exist in dev storage; MediaUrlResolver still signs it and the browser 404s on the
        // image, which is the intended "no real asset" behaviour for seed data.
        event.setCoverImageKey("events/" + organizer.getId() + "/seed-cover.jpg");
        return event;
    }

    private EventRegistration registration(Event event, User attendee, EventRegistrationStatus status,
                                           LocalDateTime checkedInAt, Long checkedInBy) {
        EventRegistration registration = new EventRegistration();
        registration.setEventId(event.getId());
        registration.setUserId(attendee.getId());
        registration.setStatus(status);
        registration.setTicketCode(UUID.randomUUID().toString());
        registration.setCheckedInAt(checkedInAt);
        registration.setCheckedInBy(checkedInBy);
        return registration;
    }

    /**
     * Mirrors EventAccessService.viewerUniversityId without importing it — the seeder builds
     * entities directly and deliberately bypasses the service layer, as every other seeder does.
     */
    private Long universityIdOf(User user) {
        if (user instanceof Student s) return s.getUniversityId();
        if (user instanceof Alumni a) return a.getUniversityId();
        if (user instanceof Club c) return c.getUniversityId();
        if (user instanceof University u) return u.getId();
        return null;
    }
}
