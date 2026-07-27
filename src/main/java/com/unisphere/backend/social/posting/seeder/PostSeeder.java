package com.unisphere.backend.social.posting.seeder;

import com.unisphere.backend.config.ObjectStorageConfig;
import com.unisphere.backend.identity.entity.Student;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.social.posting.entity.*;
import com.unisphere.backend.social.posting.enums.MediaType;
import com.unisphere.backend.social.posting.enums.PostType;
import com.unisphere.backend.social.posting.enums.PostVisibility;
import com.unisphere.backend.social.posting.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Seeds sample posts, media, tags, comments, likes, and saves.
 * Runs after UserSeeder (@Order(1)).
 * Guard: skips entirely if any post already exists.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeding.enabled", havingValue = "true", matchIfMissing = false)
public class PostSeeder implements CommandLineRunner {

    private final UserRepository       userRepository;
    private final PostRepository       postRepository;
    private final PostMediaRepository  postMediaRepository;
    private final PostTagRepository    postTagRepository;
    private final CommentRepository    commentRepository;
    private final PostLikeRepository   postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostSaveRepository   postSaveRepository;
    private final ObjectStorageConfig  storageConfig;

    @Override
    @Transactional
    public void run(String... args) {
        if (postRepository.count() > 0) {
            log.info("Posts already seeded — skipping.");
            return;
        }

        Optional<User> studentOpt    = userRepository.findByEmail("student@unisphere.dev");
        Optional<User> alumniOpt     = userRepository.findByEmail("alumni@unisphere.dev");
        Optional<User> employerOpt   = userRepository.findByEmail("employer@unisphere.dev");
        Optional<User> clubOpt       = userRepository.findByEmail("club@unisphere.dev");

        if (studentOpt.isEmpty() || alumniOpt.isEmpty() || employerOpt.isEmpty() || clubOpt.isEmpty()) {
            log.warn("PostSeeder: seeded users not found — run UserSeeder first (app.seeding.enabled=true).");
            return;
        }

        User student  = studentOpt.get();
        User alumni   = alumniOpt.get();
        User employer = employerOpt.get();
        User club     = clubOpt.get();

        // ── Posts ────────────────────────────────────────────────────────────

        Post p1 = savePost(student.getId(), PostType.TEXT, PostVisibility.PUBLIC,
                "Final Year Project Done! 🎓",
                "After months of late nights and countless cups of coffee, I finally submitted my Final Year Project on Distributed Machine Learning for Edge Devices. Huge thanks to my supervisor and teammates. Now begins the waiting game for results 😅. To all my juniors going through FYP — you've got this! #FYP #ComputerScience #UiTM");

        Post p2 = savePost(student.getId(), PostType.IMAGE, PostVisibility.PUBLIC,
                "Study Setup for Finals",
                "Sharing my study setup this exam season! Dual monitors make a huge difference when reviewing lecture slides and coding at the same time. What does your study setup look like? 💻📚 #StudyWithMe #CSStudent");

        Post p3 = savePost(alumni.getId(), PostType.TEXT, PostVisibility.PUBLIC,
                "3 Years at Petronas Digital — Lessons Learned",
                "Three years ago I walked out of UiTM with a scroll and a dream. Here's what I wish someone had told me before entering the industry:\n\n1. Your degree teaches you HOW to think, not WHAT to think.\n2. Soft skills matter just as much as technical skills.\n3. Find a mentor early — it changes everything.\n4. The imposter syndrome never fully goes away. Make peace with it.\n\nWhat's your biggest lesson from your first job? Drop it below 👇 #CareerAdvice #TechIndustry #Alumni");

        Post p4 = savePost(employer.getId(), PostType.TEXT, PostVisibility.PUBLIC,
                "We're Hiring — Junior Software Engineer 🚀",
                "TechCorp Sdn Bhd is looking for passionate Junior Software Engineers to join our growing team in Petaling Jaya!\n\n✅ Requirements:\n- Bachelor's in CS / IT / SE or equivalent\n- Familiar with Java or Python\n- Good problem-solving skills\n- Fresh graduates are welcome!\n\n💰 Salary: RM 3,000 – RM 4,500\n📍 Location: PJ (hybrid)\n\nDM us or apply at techcorp.com.my/careers. Share this with anyone who might be interested! #Hiring #SoftwareEngineer #Malaysia");

        Post p5 = savePost(club.getId(), PostType.IMAGE, PostVisibility.PUBLIC,
                "GDSC UiTM — Monthly Meetup This Saturday!",
                "Hey everyone! 👋 Google Developer Student Club UiTM is hosting our monthly meetup this Saturday, 10am at DKP2, Faculty of CS.\n\nThis month's topic: Building with Firebase — from prototype to production.\n\nFree for all UiTM students. No registration needed, just show up!\n\nSee you there 🔥 #GDSC #GoogleDeveloper #UiTM #Firebase");

        Post p6 = savePost(alumni.getId(), PostType.TEXT, PostVisibility.PUBLIC,
                "How I Landed My First Tech Job with No Internship Experience",
                "Controversial take: you don't need a fancy internship to land your first software job.\n\nI graduated with zero internship experience (long story). Here's what I did instead:\n\n📌 Built 3 personal projects and put them on GitHub\n📌 Contributed to 2 open-source repositories\n📌 Wrote 5 blog posts about things I learned\n📌 Attended every hackathon I could find\n\nSix months after graduation, I had 4 job offers. Your portfolio speaks louder than your CV.\n\n#JobSearch #FreshGraduate #TechCareer #OpenSource");

        Post p7 = savePost(student.getId(), PostType.TEXT, PostVisibility.UNIVERSITY,
                "Anyone else struggling with Algorithm Analysis assignment 3?",
                "The time complexity question for the divide-and-conquer problem is breaking my brain 🧠. Has anyone figured out the recurrence relation for part (c)? Happy to swap notes if you have! DM me. #Algorithms #CS #UiTM");
        if (student instanceof Student s && s.getUniversityId() != null) {
            // Without a real universityId here, UNIVERSITY-visibility enforcement has no seed data
            // to actually exercise — every seeded user otherwise has a null universityId.
            p7.setUniversityId(s.getUniversityId());
            postRepository.save(p7);
        }

        log.info("Seeded {} posts.", 7);

        // ── Post Media ───────────────────────────────────────────────────────

        saveMedia(p2, "posts/" + student.getId() + "/seed-study-setup.jpg",   MediaType.IMAGE, 0);
        saveMedia(p2, "posts/" + student.getId() + "/seed-study-setup-2.jpg", MediaType.IMAGE, 1);
        saveMedia(p5, "posts/" + club.getId()    + "/seed-gdsc-meetup.jpg",   MediaType.IMAGE, 0);

        log.info("Seeded post media.");

        // ── Post Tags ────────────────────────────────────────────────────────

        saveTag(p1, alumni.getId());
        saveTag(p3, student.getId());
        saveTag(p4, student.getId());
        saveTag(p4, alumni.getId());

        log.info("Seeded post tags.");

        // ── Comments ─────────────────────────────────────────────────────────

        Comment c1 = saveComment(p1.getId(), alumni.getId(), null,
                "Congratulations! FYP on distributed ML is no joke. What dataset did you use?");
        Comment c2 = saveComment(p1.getId(), employer.getId(), null,
                "Great work! If you're looking for your first job after this, TechCorp is hiring 😉");
        Comment c3 = saveComment(p1.getId(), student.getId(), null,
                "We did it bro!! 🔥 Can't believe it's finally over.");

        // Replies to c1
        Comment c1r1 = saveComment(p1.getId(), student.getId(), c1.getId(),
                "Thanks! I used a custom edge dataset collected from IoT sensors on campus. Will write about it soon!");
        Comment c1r2 = saveComment(p1.getId(), alumni.getId(), c1r1.getId(),
                "That sounds really interesting. Would love to read more about it when you publish!");

        Comment c4 = saveComment(p3.getId(), student.getId(), null,
                "This is exactly what I needed to hear before entering the workforce. Saving this post!");
        Comment c5 = saveComment(p3.getId(), employer.getId(), null,
                "Point 2 is so underrated. We actually rate communication skills higher than technical skills during interviews.");
        Comment c5r1 = saveComment(p3.getId(), alumni.getId(), c5.getId(),
                "Totally agree. I've seen brilliant developers get passed over because they couldn't explain their work clearly.");

        Comment c6 = saveComment(p4.getId(), student.getId(), null,
                "Just applied! Hope I hear back soon 🤞");
        Comment c7 = saveComment(p4.getId(), alumni.getId(), null,
                "Shared to my network. Great opportunity for fresh grads!");
        Comment c7r1 = saveComment(p4.getId(), employer.getId(), c7.getId(),
                "Thank you! We value referrals highly 🙌");

        Comment c8 = saveComment(p5.getId(), student.getId(), null,
                "Will be there! Firebase is exactly what I need for my side project.");
        Comment c9 = saveComment(p5.getId(), alumni.getId(), null,
                "Wish I was still a student so I could join these! Miss the GDSC vibes.");
        Comment c9r1 = saveComment(p5.getId(), club.getId(), c9.getId(),
                "Alumni are always welcome! Come back and share your experience with us 🙏");

        Comment c10 = saveComment(p6.getId(), student.getId(), null,
                "This gave me so much hope. Currently building my first proper project on GitHub right now!");
        Comment c11 = saveComment(p6.getId(), employer.getId(), null,
                "100% agree. We hired someone last month purely based on their open-source contributions. CV was basic but portfolio was brilliant.");

        log.info("Seeded {} comments.", 12);

        // ── Post Likes ───────────────────────────────────────────────────────

        likePost(p1, alumni.getId());
        likePost(p1, employer.getId());
        likePost(p1, club.getId());

        likePost(p2, alumni.getId());
        likePost(p2, club.getId());

        likePost(p3, student.getId());
        likePost(p3, employer.getId());
        likePost(p3, club.getId());

        likePost(p4, student.getId());
        likePost(p4, alumni.getId());
        likePost(p4, club.getId());

        likePost(p5, student.getId());
        likePost(p5, alumni.getId());

        likePost(p6, student.getId());
        likePost(p6, employer.getId());
        likePost(p6, club.getId());

        likePost(p7, alumni.getId());

        // Sync likes_count to DB (Redis flush won't run yet during seeding)
        syncPostLikesCount(p1, p2, p3, p4, p5, p6, p7);

        log.info("Seeded post likes.");

        // ── Comment Likes ────────────────────────────────────────────────────

        likeComment(c1,   student.getId());
        likeComment(c1,   employer.getId());
        likeComment(c2,   student.getId());
        likeComment(c3,   alumni.getId());
        likeComment(c4,   alumni.getId());
        likeComment(c4,   employer.getId());
        likeComment(c5,   student.getId());
        likeComment(c5,   alumni.getId());
        likeComment(c5r1, student.getId());
        likeComment(c7,   employer.getId());
        likeComment(c9r1, alumni.getId());
        likeComment(c10,  alumni.getId());
        likeComment(c10,  employer.getId());
        likeComment(c11,  student.getId());
        likeComment(c11,  alumni.getId());

        syncCommentLikesCount(c1, c2, c3, c4, c5, c5r1, c7, c9r1, c10, c11);

        log.info("Seeded comment likes.");

        // ── Post Saves ───────────────────────────────────────────────────────

        savePostSave(p1, alumni.getId());
        savePostSave(p3, student.getId());
        savePostSave(p3, club.getId());
        savePostSave(p4, student.getId());
        savePostSave(p4, alumni.getId());
        savePostSave(p6, student.getId());
        savePostSave(p6, employer.getId());
        savePostSave(p6, club.getId());

        log.info("Seeded post saves.");
        log.info("Post seeding complete.");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Post savePost(Long userId, PostType type, PostVisibility visibility,
                          String title, String content) {
        Post p = new Post();
        p.setUserId(userId);
        p.setPostType(type);
        p.setVisibility(visibility);
        p.setTitle(title);
        p.setContent(content);
        return postRepository.save(p);
    }

    private void saveMedia(Post post, String key, MediaType type, int order) {
        PostMedia m = new PostMedia();
        m.setPost(post);
        m.setMediaUrl(storageConfig.resolveMediaUrl(key));
        m.setMediaType(type);
        m.setSortOrder(order);
        postMediaRepository.save(m);
    }

    private void saveTag(Post post, Long taggedUserId) {
        PostTag t = new PostTag();
        t.setPost(post);
        t.setTaggedUserId(taggedUserId);
        postTagRepository.save(t);
    }

    private Comment saveComment(Long postId, Long userId, Long parentId, String content) {
        Comment c = new Comment();
        c.setPostId(postId);
        c.setUserId(userId);
        c.setParentCommentId(parentId);
        c.setContent(content);
        return commentRepository.save(c);
    }

    private void likePost(Post post, Long userId) {
        if (!postLikeRepository.existsByPostIdAndUserId(post.getId(), userId)) {
            postLikeRepository.save(new PostLike(post.getId(), userId));
        }
    }

    private void likeComment(Comment comment, Long userId) {
        if (!commentLikeRepository.existsByCommentIdAndUserId(comment.getId(), userId)) {
            commentLikeRepository.save(new CommentLike(comment.getId(), userId));
        }
    }

    private void savePostSave(Post post, Long userId) {
        if (!postSaveRepository.existsByUserIdAndPostId(userId, post.getId())) {
            postSaveRepository.save(new PostSave(userId, post.getId()));
        }
    }

    /** Sync likes_count directly since the Redis flush scheduler hasn't run yet. */
    private void syncPostLikesCount(Post... posts) {
        for (Post post : posts) {
            long count = postLikeRepository.countByPostId(post.getId());
            if (count > 0) {
                postRepository.incrementLikesCount(post.getId(), count);
            }
        }
    }

    private void syncCommentLikesCount(Comment... comments) {
        for (Comment comment : comments) {
            long count = commentLikeRepository.countByCommentId(comment.getId());
            if (count > 0) {
                commentRepository.incrementLikesCount(comment.getId(), count);
            }
        }
    }
}
