package com.unisphere.backend.campus.news.service;

import com.unisphere.backend.campus.news.entity.NewsArticle;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import com.unisphere.backend.campus.news.enums.NewsVisibility;
import com.unisphere.backend.common.exception.NewsAuthoringNotAllowedException;
import com.unisphere.backend.identity.entity.Alumni;
import com.unisphere.backend.identity.entity.Club;
import com.unisphere.backend.identity.entity.Role;
import com.unisphere.backend.identity.entity.Student;
import com.unisphere.backend.identity.entity.University;
import com.unisphere.backend.identity.entity.User;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

/**
 * Who may write news, and who may read a given article. Mirrors
 * {@code social.posting.service.PostAccessService}.
 */
@Service
public class NewsAccessService {

    /** Institutional accounts only — students and alumni are readers. */
    private static final Set<Role> AUTHOR_ROLES =
            EnumSet.of(Role.UNIVERSITY, Role.EMPLOYER, Role.CLUB, Role.ADMIN);

    public boolean canAuthor(User user) {
        return AUTHOR_ROLES.contains(user.getRole());
    }

    public void assertCanAuthor(User user) {
        if (!canAuthor(user)) {
            throw new NewsAuthoringNotAllowedException(user.getRole());
        }
    }

    public boolean canView(NewsArticle article, User viewer) {
        if (article.getAuthorId().equals(viewer.getId())) return true;
        if (viewer.getRole() == Role.ADMIN) return true;
        // DRAFT covers both plain drafts and scheduled-but-not-yet-due articles.
        if (article.getStatus() == NewsStatus.DRAFT) return false;
        // ARCHIVED stays readable by direct link — a permalink that 404s punishes readers for an
        // editorial action. Feeds and search exclude it by pinning status = PUBLISHED instead.
        return switch (article.getVisibility()) {
            case PUBLIC -> true;
            case UNIVERSITY -> article.getUniversityId() != null
                    && article.getUniversityId().equals(viewerUniversityId(viewer));
        };
    }

    /**
     * The viewer's university, or null.
     *
     * <p>Deliberately NOT {@code UserService.universityIdOf}: that helper returns null for a
     * University account, which would leave a university's own UNIVERSITY-scoped article with a
     * null universityId and therefore invisible to its own students. The
     * {@code University u -> u.getId()} branch below is the whole difference.
     */
    public Long viewerUniversityId(User user) {
        if (user instanceof Student s)    return s.getUniversityId();
        if (user instanceof Alumni a)     return a.getUniversityId();
        if (user instanceof Club c)       return c.getUniversityId();
        if (user instanceof University u) return u.getId();
        return null; // Employer, Admin — no university affiliation
    }

    /**
     * The universityId to stamp on an article, derived from its author. Never read from the
     * request — UNIVERSITY visibility's guarantee depends on it matching a real affiliation.
     */
    public Long resolveArticleUniversityId(User author, NewsVisibility visibility) {
        if (visibility != NewsVisibility.UNIVERSITY) return null;
        Long id = viewerUniversityId(author);
        if (id == null) {
            // Without this, an Employer or Admin could create a UNIVERSITY article whose null
            // universityId no predicate can ever match — invisible to everyone, silently.
            throw new IllegalArgumentException(
                    "UNIVERSITY visibility requires a university affiliation");
        }
        return id;
    }
}
