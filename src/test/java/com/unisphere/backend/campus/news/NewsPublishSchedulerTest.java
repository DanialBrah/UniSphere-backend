package com.unisphere.backend.campus.news;

import com.unisphere.backend.campus.news.entity.NewsArticle;
import com.unisphere.backend.campus.news.enums.NewsStatus;
import com.unisphere.backend.campus.news.repository.NewsArticleRepository;
import com.unisphere.backend.campus.news.service.NewsPublishScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Articles are persisted through the repository rather than the API here: {@code @Future} on
 * {@code scheduledAt} makes an already-due article unconstructible over HTTP, which is exactly the
 * state the sweep exists to handle.
 */
class NewsPublishSchedulerTest extends AbstractNewsIntegrationTest {

    @Autowired
    private NewsArticleRepository newsArticleRepository;

    @Autowired
    private NewsPublishScheduler newsPublishScheduler;

    private NewsArticle persistScheduledDraft(Long authorId, LocalDateTime scheduledAt) {
        NewsArticle article = new NewsArticle();
        article.setAuthorId(authorId);
        article.setTitle("Scheduled headline");
        article.setContent("Body text here");
        article.setStatus(NewsStatus.DRAFT);
        article.setScheduledAt(scheduledAt);
        return newsArticleRepository.save(article);
    }

    @Test
    void dueDraftIsPublishedWithPublishedAtTakenFromScheduledAt() throws Exception {
        Long authorId = getUserId(registerUniversityAndGetToken("nps.due@test.com", "Due University"));
        LocalDateTime due = LocalDateTime.now().minusMinutes(5).withNano(0);
        Long articleId = persistScheduledDraft(authorId, due).getId();

        newsPublishScheduler.publishDueArticles();

        NewsArticle published = newsArticleRepository.findById(articleId).orElseThrow();
        assertThat(published.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isEqualTo(due);
        assertThat(published.getScheduledAt()).isNull();
    }

    @Test
    void aSecondSweepPublishesNothing() throws Exception {
        Long authorId = getUserId(registerUniversityAndGetToken("nps.idem@test.com", "Idem University"));
        persistScheduledDraft(authorId, LocalDateTime.now().minusMinutes(5).withNano(0));

        // The repository method is @Modifying(flushAutomatically) and so needs a transaction —
        // in production NewsPublishScheduler supplies one. Calling it directly here (rather than
        // through the scheduler) is what makes the returned row count observable.
        int first = transactionTemplate.execute(s -> newsArticleRepository.publishDueArticles(LocalDateTime.now()));
        int second = transactionTemplate.execute(s -> newsArticleRepository.publishDueArticles(LocalDateTime.now()));

        // The status = DRAFT guard is what makes the sweep safe to run on every replica: a second
        // instance ticking at the same moment matches zero rows instead of double-publishing.
        assertThat(first).isPositive();
        assertThat(second).isZero();
    }

    @Test
    void futureScheduledArticleIsLeftAlone() throws Exception {
        Long authorId = getUserId(registerUniversityAndGetToken("nps.future@test.com", "Future University"));
        Long articleId = persistScheduledDraft(authorId, LocalDateTime.now().plusDays(1).withNano(0)).getId();

        newsPublishScheduler.publishDueArticles();

        assertThat(newsArticleRepository.findById(articleId).orElseThrow().getStatus())
                .isEqualTo(NewsStatus.DRAFT);
    }

    @Test
    void softDeletedScheduledArticleIsNotResurrected() throws Exception {
        // @SQLRestriction does not apply to bulk JPQL updates, so publishDueArticles has to filter
        // deleted_at by hand. Without that, a deleted draft would publish itself.
        Long authorId = getUserId(registerUniversityAndGetToken("nps.deleted@test.com", "Deleted University"));
        NewsArticle article = persistScheduledDraft(authorId, LocalDateTime.now().minusMinutes(5).withNano(0));
        article.setDeletedAt(LocalDateTime.now());
        newsArticleRepository.save(article);
        Long articleId = article.getId();

        newsPublishScheduler.publishDueArticles();

        // findById honours @SQLRestriction, so a soft-deleted row is simply absent.
        assertThat(newsArticleRepository.findById(articleId)).isEmpty();
        assertThat(newsArticleRepository.findActiveById(articleId)).isEmpty();
    }

    @Test
    void updatedAtAdvancesOnBulkPublish() throws Exception {
        // Auditing does not fire on bulk updates, so the query sets updatedAt explicitly.
        Long authorId = getUserId(registerUniversityAndGetToken("nps.audit@test.com", "Audit University"));
        NewsArticle article = persistScheduledDraft(authorId, LocalDateTime.now().minusMinutes(5).withNano(0));
        LocalDateTime before = article.getUpdatedAt();
        Long articleId = article.getId();

        Thread.sleep(10);
        newsPublishScheduler.publishDueArticles();

        assertThat(newsArticleRepository.findById(articleId).orElseThrow().getUpdatedAt())
                .isAfter(before);
    }
}
