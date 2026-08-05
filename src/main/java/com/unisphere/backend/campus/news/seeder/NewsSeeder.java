package com.unisphere.backend.campus.news.seeder;

import com.unisphere.backend.campus.news.entity.*;
import com.unisphere.backend.campus.news.enums.NewsCategory;
import com.unisphere.backend.campus.news.enums.NewsMediaType;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import com.unisphere.backend.campus.news.enums.NewsVisibility;
import com.unisphere.backend.campus.news.repository.*;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Seeds sample news articles, media, tags, comments, likes, and saves.
 * Runs after MessagingSeeder (@Order(3)).
 * Guard: skips entirely if any article already exists.
 *
 * <p>Covers every state the module can be in, so the frontend has something to render for each:
 * published (featured and not), university-scoped, draft, scheduled-but-not-due, and archived.
 */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeding.enabled", havingValue = "true", matchIfMissing = false)
public class NewsSeeder implements CommandLineRunner {

    private final UserRepository            userRepository;
    private final NewsArticleRepository     newsArticleRepository;
    private final NewsMediaRepository       newsMediaRepository;
    private final NewsTagRepository         newsTagRepository;
    private final NewsCommentRepository     newsCommentRepository;
    private final NewsLikeRepository        newsLikeRepository;
    private final NewsCommentLikeRepository newsCommentLikeRepository;
    private final NewsSaveRepository        newsSaveRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (newsArticleRepository.count() > 0) {
            log.info("News articles already seeded — skipping.");
            return;
        }

        Optional<User> universityOpt = userRepository.findByEmail("university@unisphere.dev");
        Optional<User> clubOpt       = userRepository.findByEmail("club@unisphere.dev");
        Optional<User> employerOpt   = userRepository.findByEmail("employer@unisphere.dev");
        Optional<User> adminOpt      = userRepository.findByEmail("admin@unisphere.dev");
        Optional<User> studentOpt    = userRepository.findByEmail("student@unisphere.dev");
        Optional<User> alumniOpt     = userRepository.findByEmail("alumni@unisphere.dev");

        if (universityOpt.isEmpty() || clubOpt.isEmpty() || employerOpt.isEmpty()
                || adminOpt.isEmpty() || studentOpt.isEmpty() || alumniOpt.isEmpty()) {
            log.warn("NewsSeeder: seeded users not found — run UserSeeder first (app.seeding.enabled=true).");
            return;
        }

        User university = universityOpt.get();
        User club       = clubOpt.get();
        User employer   = employerOpt.get();
        User admin      = adminOpt.get();
        User student    = studentOpt.get();
        User alumni     = alumniOpt.get();

        LocalDateTime now = LocalDateTime.now();

        // ── Articles ─────────────────────────────────────────────────────────
        // Only UNIVERSITY, EMPLOYER, CLUB and ADMIN may author — student and alumni
        // appear below purely as readers.

        NewsArticle a1 = article(university.getId(), NewsCategory.ACADEMIC,
                "UiTM Opens Applications for Semester 2 Exchange Programme",
                "Applications are now open for the Semester 2 student exchange programme, with partner "
                + "universities across Japan, South Korea, Germany and the Netherlands.\n\n"
                + "Eligible students must have completed at least two semesters with a minimum CGPA of 3.00 "
                + "and no outstanding disciplinary record. Partial scholarships covering tuition and "
                + "accommodation are available for twelve places.\n\n"
                + "Applications close on the last Friday of the month. Briefing sessions will be held at the "
                + "International Office throughout the application window — no registration required.",
                "Twelve partial scholarships are available across four partner countries.");
        publish(a1, now.minusDays(1));
        a1.setFeatured(true);
        newsArticleRepository.save(a1);

        NewsArticle a2 = article(club.getId(), NewsCategory.EVENTS,
                "GDSC UiTM Wraps Up Its Largest Firebase Workshop Yet",
                "More than 180 students packed DKP2 last Saturday for the Google Developer Student Club's "
                + "monthly meetup — the largest turnout since the chapter was founded.\n\n"
                + "The session walked attendees from an empty project to a deployed application, covering "
                + "Firestore modelling, authentication and hosting. Participants left with a working "
                + "prototype and a repository they could keep building on.\n\n"
                + "The club has confirmed that next month's session will cover cloud functions and "
                + "background jobs. Slides and the sample repository are available to all attendees.",
                "A record 180 students attended the club's monthly developer meetup.");
        publish(a2, now.minusDays(2));

        NewsArticle a3 = article(employer.getId(), NewsCategory.TECH,
                "TechCorp Opens 25 Graduate Positions for the Coming Intake",
                "TechCorp Sdn Bhd has announced 25 graduate openings across software engineering, data and "
                + "quality assurance for its Petaling Jaya office.\n\n"
                + "The company confirmed that fresh graduates are encouraged to apply and that no prior "
                + "internship experience is required. Candidates will be assessed on a take-home exercise "
                + "rather than a whiteboard interview, a change introduced after feedback from last year's "
                + "cohort.\n\n"
                + "A campus recruitment session is being scheduled for later this semester.",
                "No prior internship required; assessment moves to a take-home exercise.");
        publish(a3, now.minusDays(3));

        NewsArticle a4 = article(university.getId(), NewsCategory.SPORTS,
                "Faculty League Final Ends in Penalties After Goalless Draw",
                "The inter-faculty football final went to penalties on Sunday evening after ninety minutes "
                + "and extra time failed to separate the Faculty of Computer Science and the Faculty of "
                + "Engineering.\n\n"
                + "Computer Science took the title 4-3 on penalties in front of a full stand at the main "
                + "field. It is the faculty's first league title in six years.\n\n"
                + "The full fixture archive and next season's schedule will be published by the Sports "
                + "Centre before the semester break.",
                "Computer Science take the title 4-3 on penalties — their first in six years.");
        publish(a4, now.minusDays(4));

        NewsArticle a5 = article(admin.getId(), NewsCategory.GENERAL,
                "UniSphere Adds Campus News to the Student Portal",
                "Campus news is now available to every UniSphere account. Universities, clubs and partner "
                + "employers can publish directly to the feed, and readers can follow topics through tags, "
                + "filter by category, and save articles to read later.\n\n"
                + "Articles can be scoped to a single university where the content is only relevant to that "
                + "community, or published openly to everyone on the platform.\n\n"
                + "Comments are open on published articles. Report anything that looks out of place through "
                + "the usual support channel.",
                "Universities, clubs and employers can now publish directly to the student feed.");
        publish(a5, now.minusHours(6));
        a5.setFeatured(true);
        newsArticleRepository.save(a5);

        // University-scoped: only readers affiliated with this university can see it. Authored by
        // the university itself because UserSeeder links the seeded student and alumni to it —
        // the seeded club has a null universityId, so a club-authored UNIVERSITY article would be
        // stamped null and become invisible to everyone. See NewsAccessService.
        NewsArticle a6 = article(university.getId(), NewsCategory.ACADEMIC,
                "Internal: Revised Examination Timetable for Final-Year Cohorts",
                "The examination timetable for final-year cohorts has been revised following the addition of "
                + "a replacement slot for the postponed Algorithm Analysis paper.\n\n"
                + "Affected students will receive an individual notification through the portal. The venue "
                + "remains unchanged; only the date and session have moved.\n\n"
                + "Queries should go through your faculty's academic office rather than the examinations "
                + "unit directly, so that records stay consistent.",
                "Only the Algorithm Analysis paper has moved; venues are unchanged.");
        a6.setVisibility(NewsVisibility.UNIVERSITY);
        a6.setUniversityId(university.getId());
        publish(a6, now.minusHours(12));

        // Draft — visible to its author and admins only.
        NewsArticle a7 = article(university.getId(), NewsCategory.GENERAL,
                "Draft: Library Opening Hours for the Study Break",
                "Extended opening hours for the study break are being finalised with the Library "
                + "Directorate. Figures below are provisional and must not be published until confirmed.\n\n"
                + "Proposed: 24-hour access to the main reading hall for the two weeks preceding finals, "
                + "with the annexe closing at midnight.",
                "Provisional hours — pending confirmation from the Library Directorate.");
        newsArticleRepository.save(a7);

        // Scheduled — still a DRAFT, but NewsPublishScheduler will flip it once due.
        NewsArticle a8 = article(club.getId(), NewsCategory.EVENTS,
                "Hackathon 2.0 Registration Opens This Weekend",
                "Registration for the second edition of the campus hackathon opens this weekend, with a "
                + "48-hour build window and tracks in sustainability, education and campus life.\n\n"
                + "Teams of up to four may register. Hardware will be available on loan from the faculty "
                + "makerspace, and mentors from three partner companies will be on site for the duration.\n\n"
                + "Last year's edition drew 60 teams; capacity has been raised to 90 for this run.",
                "48 hours, three tracks, and capacity raised to 90 teams.");
        a8.setScheduledAt(now.plusDays(2));
        newsArticleRepository.save(a8);

        // Archived — drops out of the feed but stays reachable by direct link.
        NewsArticle a9 = article(university.getId(), NewsCategory.ALUMNI,
                "Alumni Homecoming Dinner — Thank You for Attending",
                "Thank you to the 400 alumni who joined this year's homecoming dinner at the Chancellery "
                + "Hall. The evening raised a record sum for the student hardship fund.\n\n"
                + "Photographs from the night have been shared with attendees. Details of next year's "
                + "gathering will be announced in due course.",
                "A record sum raised for the student hardship fund.");
        publish(a9, now.minusDays(40));
        a9.setStatus(NewsStatus.ARCHIVED);
        newsArticleRepository.save(a9);

        log.info("Seeded {} news articles (published, university-scoped, draft, scheduled, archived).", 9);

        // ── Tags ─────────────────────────────────────────────────────────────

        tags(a1, "exchange", "scholarship", "international", "academics");
        tags(a2, "gdsc", "firebase", "workshop", "developers");
        tags(a3, "hiring", "graduates", "techcorp", "careers");
        tags(a4, "football", "inter-faculty", "sports");
        tags(a5, "platform", "announcement", "unisphere");
        tags(a6, "examinations", "final-year", "academics");
        tags(a7, "library", "study-break");
        tags(a8, "hackathon", "gdsc", "developers");
        tags(a9, "alumni", "homecoming");

        log.info("Seeded news tags.");

        // ── Media ────────────────────────────────────────────────────────────
        // Bare object keys under news/{authorId}/ — never resolved URLs. Reads mint a
        // presigned GET through MediaUrlResolver.

        a1.setCoverImageKey("news/" + university.getId() + "/seed-exchange-cover.jpg");
        a2.setCoverImageKey("news/" + club.getId() + "/seed-firebase-cover.jpg");
        a3.setCoverImageKey("news/" + employer.getId() + "/seed-hiring-cover.jpg");
        a4.setCoverImageKey("news/" + university.getId() + "/seed-football-cover.jpg");
        a5.setCoverImageKey("news/" + admin.getId() + "/seed-platform-cover.jpg");
        a8.setCoverImageKey("news/" + club.getId() + "/seed-hackathon-cover.jpg");
        newsArticleRepository.saveAll(List.of(a1, a2, a3, a4, a5, a8));

        media(a2, "news/" + club.getId() + "/seed-firebase-1.jpg", NewsMediaType.IMAGE, 0);
        media(a2, "news/" + club.getId() + "/seed-firebase-2.jpg", NewsMediaType.IMAGE, 1);
        media(a4, "news/" + university.getId() + "/seed-football-1.jpg", NewsMediaType.IMAGE, 0);
        media(a4, "news/" + university.getId() + "/seed-football-highlights.mp4", NewsMediaType.VIDEO, 1);

        log.info("Seeded news media.");

        // ── Comments ─────────────────────────────────────────────────────────
        // Only on published articles — the API refuses comments on drafts and archived pieces.

        NewsComment n1c1 = comment(a1.getId(), student.getId(), null,
                "Is the CGPA requirement calculated on the latest semester or the cumulative average?");
        comment(a1.getId(), university.getId(), n1c1.getId(),
                "Cumulative. The International Office can confirm your standing if you're unsure.");
        NewsComment n1c2 = comment(a1.getId(), alumni.getId(), null,
                "Did the exchange in my third year and it genuinely changed my career direction. Apply.");

        NewsComment n2c1 = comment(a2.getId(), student.getId(), null,
                "Was there and it was excellent. Any chance the slides get posted publicly?");
        comment(a2.getId(), club.getId(), n2c1.getId(),
                "Sharing them with attendees this week — check your email.");
        NewsComment n2c2 = comment(a2.getId(), alumni.getId(), null,
                "180 people is impressive. It was about 30 when I helped start the chapter.");

        NewsComment n3c1 = comment(a3.getId(), student.getId(), null,
                "A take-home instead of a whiteboard is a very welcome change.");
        comment(a3.getId(), employer.getId(), n3c1.getId(),
                "It gave us a much better signal last cycle, so we've made it permanent.");
        NewsComment n3c2 = comment(a3.getId(), alumni.getId(), null,
                "Shared with a few juniors who are graduating this semester.");

        NewsComment n4c1 = comment(a4.getId(), student.getId(), null,
                "Six years! Worth the wait. The keeper deserves most of the credit.");
        NewsComment n5c1 = comment(a5.getId(), student.getId(), null,
                "The tag filter is genuinely useful. Following the academics tag now.");
        comment(a5.getId(), alumni.getId(), null,
                "Good to see employers posting here rather than only on the jobs board.");

        NewsComment n6c1 = comment(a6.getId(), student.getId(), null,
                "Received the notification, thanks. The new slot doesn't clash with anything for me.");

        log.info("Seeded {} news comments.", 14);

        // ── Article likes ────────────────────────────────────────────────────

        like(a1, student.getId());
        like(a1, alumni.getId());
        like(a1, club.getId());
        like(a1, employer.getId());

        like(a2, student.getId());
        like(a2, alumni.getId());
        like(a2, university.getId());

        like(a3, student.getId());
        like(a3, alumni.getId());

        like(a4, student.getId());
        like(a4, club.getId());

        like(a5, student.getId());
        like(a5, alumni.getId());
        like(a5, club.getId());
        like(a5, employer.getId());
        like(a5, university.getId());

        like(a6, student.getId());
        like(a9, alumni.getId());

        // The Redis flush scheduler hasn't run yet, so likes_count is synced directly.
        syncArticleLikesCount(a1, a2, a3, a4, a5, a6, a9);

        log.info("Seeded news likes.");

        // ── Comment likes ────────────────────────────────────────────────────

        likeComment(n1c1, alumni.getId());
        likeComment(n1c2, student.getId());
        likeComment(n1c2, university.getId());
        likeComment(n2c1, club.getId());
        likeComment(n2c2, student.getId());
        likeComment(n2c2, club.getId());
        likeComment(n3c1, alumni.getId());
        likeComment(n3c1, employer.getId());
        likeComment(n3c2, student.getId());
        likeComment(n4c1, university.getId());
        likeComment(n5c1, admin.getId());
        likeComment(n6c1, university.getId());

        syncCommentLikesCount(n1c1, n1c2, n2c1, n2c2, n3c1, n3c2, n4c1, n5c1, n6c1);

        log.info("Seeded news comment likes.");

        // ── Saves ────────────────────────────────────────────────────────────

        save(a1, student.getId());
        save(a1, alumni.getId());
        save(a3, student.getId());
        save(a3, alumni.getId());
        save(a5, student.getId());
        save(a6, student.getId());

        log.info("Seeded news saves.");
        log.info("News seeding complete.");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** A PUBLIC DRAFT with no scheduling — callers promote it from there. */
    private NewsArticle article(Long authorId, NewsCategory category,
                                String title, String content, String summary) {
        NewsArticle a = new NewsArticle();
        a.setAuthorId(authorId);
        a.setCategory(category);
        a.setTitle(title);
        a.setContent(content);
        a.setSummary(summary);
        a.setVisibility(NewsVisibility.PUBLIC);
        a.setStatus(NewsStatus.DRAFT);
        return newsArticleRepository.save(a);
    }

    /** Staggered publishedAt values so the feed's newest-first ordering is visible in seed data. */
    private void publish(NewsArticle article, LocalDateTime publishedAt) {
        article.setStatus(NewsStatus.PUBLISHED);
        article.setPublishedAt(publishedAt);
        article.setScheduledAt(null);
        newsArticleRepository.save(article);
    }

    private void tags(NewsArticle article, String... values) {
        for (String value : values) {
            NewsTag tag = new NewsTag();
            tag.setArticle(article);
            tag.setTag(value);
            newsTagRepository.save(tag);
        }
    }

    private void media(NewsArticle article, String key, NewsMediaType type, int order) {
        NewsMedia m = new NewsMedia();
        m.setArticle(article);
        m.setMediaKey(key);
        m.setMediaType(type);
        m.setSortOrder(order);
        newsMediaRepository.save(m);
    }

    private NewsComment comment(Long articleId, Long userId, Long parentId, String content) {
        NewsComment c = new NewsComment();
        c.setArticleId(articleId);
        c.setUserId(userId);
        c.setParentCommentId(parentId);
        c.setContent(content);
        return newsCommentRepository.save(c);
    }

    private void like(NewsArticle article, Long userId) {
        if (!newsLikeRepository.existsByArticleIdAndUserId(article.getId(), userId)) {
            newsLikeRepository.save(new NewsLike(article.getId(), userId));
        }
    }

    private void likeComment(NewsComment comment, Long userId) {
        if (!newsCommentLikeRepository.existsByCommentIdAndUserId(comment.getId(), userId)) {
            newsCommentLikeRepository.save(new NewsCommentLike(comment.getId(), userId));
        }
    }

    private void save(NewsArticle article, Long userId) {
        if (!newsSaveRepository.existsByUserIdAndArticleId(userId, article.getId())) {
            newsSaveRepository.save(new NewsSave(userId, article.getId()));
        }
    }

    /** Sync likes_count directly since the Redis flush scheduler hasn't run yet. */
    private void syncArticleLikesCount(NewsArticle... articles) {
        for (NewsArticle article : articles) {
            long count = newsLikeRepository.countByArticleId(article.getId());
            if (count > 0) {
                newsArticleRepository.incrementLikesCount(article.getId(), count);
            }
        }
    }

    private void syncCommentLikesCount(NewsComment... comments) {
        for (NewsComment comment : comments) {
            long count = newsCommentLikeRepository.countByCommentId(comment.getId());
            if (count > 0) {
                newsCommentRepository.incrementLikesCount(comment.getId(), count);
            }
        }
    }
}
