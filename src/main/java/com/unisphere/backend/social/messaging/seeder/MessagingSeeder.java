package com.unisphere.backend.social.messaging.seeder;

import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.social.messaging.entity.Conversation;
import com.unisphere.backend.social.messaging.entity.ConversationMember;
import com.unisphere.backend.social.messaging.entity.Message;
import com.unisphere.backend.social.messaging.entity.MessageRead;
import com.unisphere.backend.social.messaging.enums.ConversationType;
import com.unisphere.backend.social.messaging.enums.MemberRole;
import com.unisphere.backend.social.messaging.enums.MessageType;
import com.unisphere.backend.social.messaging.repository.ConversationMemberRepository;
import com.unisphere.backend.social.messaging.repository.ConversationRepository;
import com.unisphere.backend.social.messaging.repository.MessageReadRepository;
import com.unisphere.backend.social.messaging.repository.MessageRepository;
import com.unisphere.backend.social.notification.entity.Notification;
import com.unisphere.backend.social.notification.enums.NotificationType;
import com.unisphere.backend.social.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Seeds sample conversations, messages, read receipts, and notifications.
 * Runs after PostSeeder (@Order(2)).
 * Guard: skips entirely if any conversation already exists.
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seeding.enabled", havingValue = "true", matchIfMissing = false)
public class MessagingSeeder implements CommandLineRunner {

    private final UserRepository             userRepository;
    private final ConversationRepository     conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository          messageRepository;
    private final MessageReadRepository      messageReadRepository;
    private final NotificationRepository     notificationRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (conversationRepository.count() > 0) {
            log.info("Conversations already seeded — skipping.");
            return;
        }

        Optional<User> studentOpt  = userRepository.findByEmail("student@unisphere.dev");
        Optional<User> alumniOpt   = userRepository.findByEmail("alumni@unisphere.dev");
        Optional<User> employerOpt = userRepository.findByEmail("employer@unisphere.dev");
        Optional<User> clubOpt     = userRepository.findByEmail("club@unisphere.dev");

        if (studentOpt.isEmpty() || alumniOpt.isEmpty() || employerOpt.isEmpty() || clubOpt.isEmpty()) {
            log.warn("MessagingSeeder: seeded users not found — run UserSeeder first.");
            return;
        }

        User student  = studentOpt.get();
        User alumni   = alumniOpt.get();
        User employer = employerOpt.get();
        User club     = clubOpt.get();

        // ── DM: student ↔ alumni (career advice) ────────────────────────────

        Conversation dm1 = saveConversation(ConversationType.DIRECT, null, student.getId());
        addMember(dm1, student.getId(),  MemberRole.ADMIN);
        addMember(dm1, alumni.getId(),   MemberRole.MEMBER);

        Message dm1m1 = saveMessage(dm1.getId(), alumni.getId(),
                "Hey! Saw your FYP post — distributed ML is impressive. What stack did you use?", null);
        Message dm1m2 = saveMessage(dm1.getId(), student.getId(),
                "Thanks! I used PyTorch for the model and MQTT for edge communication. Still polishing the paper though.", null);
        Message dm1m3 = saveMessage(dm1.getId(), alumni.getId(),
                "Nice stack. If you're looking for a job after graduation, drop me your CV — we have openings at Petronas Digital for fresh grads.", null);
        Message dm1m4 = saveMessage(dm1.getId(), student.getId(),
                "Really?! That would be amazing. I'll DM you my portfolio link once I clean it up.", null);
        Message dm1m5 = saveMessage(dm1.getId(), alumni.getId(),
                "Take your time. Good luck with the submission!", null);

        markRead(dm1m1, student.getId());
        markRead(dm1m2, alumni.getId());
        markRead(dm1m3, student.getId());
        markRead(dm1m4, alumni.getId());

        log.info("Seeded DM: student ↔ alumni ({} messages).", 5);

        // ── DM: student ↔ employer (job inquiry) ────────────────────────────

        Conversation dm2 = saveConversation(ConversationType.DIRECT, null, student.getId());
        addMember(dm2, student.getId(),  MemberRole.ADMIN);
        addMember(dm2, employer.getId(), MemberRole.MEMBER);

        Message dm2m1 = saveMessage(dm2.getId(), student.getId(),
                "Hi! I just applied for the Junior Software Engineer role at TechCorp. Wanted to introduce myself — I'm a final-year CS student at UiTM.", null);
        Message dm2m2 = saveMessage(dm2.getId(), employer.getId(),
                "Welcome! Great to hear. We'll review your application this week. Do you have a GitHub or portfolio link?", null);
        Message dm2m3 = saveMessage(dm2.getId(), student.getId(),
                "Yes! github.com/student-dev — my FYP code is there along with a few side projects.", null);
        Message dm2m4 = saveMessage(dm2.getId(), employer.getId(),
                "Excellent. We'll be in touch by Friday. 🙂", null);

        markRead(dm2m1, employer.getId());
        markRead(dm2m2, student.getId());
        markRead(dm2m3, employer.getId());

        log.info("Seeded DM: student ↔ employer ({} messages).", 4);

        // ── Group: student + alumni + club (CS Study Group) ─────────────────

        Conversation group1 = saveConversation(ConversationType.GROUP, "CS Study Group 📚", club.getId());
        addMember(group1, club.getId(),    MemberRole.ADMIN);
        addMember(group1, student.getId(), MemberRole.MEMBER);
        addMember(group1, alumni.getId(),  MemberRole.MEMBER);

        Message g1m1 = saveMessage(group1.getId(), club.getId(),
                "Welcome everyone to the CS Study Group! This is where we coordinate study sessions and share resources. 🎉", null);
        Message g1m2 = saveMessage(group1.getId(), student.getId(),
                "Thanks for setting this up! Really needed a dedicated space for this.", null);
        Message g1m3 = saveMessage(group1.getId(), alumni.getId(),
                "Happy to be here too. I can share some resources from my time at UiTM if that helps.", null);
        Message g1m4 = saveMessage(group1.getId(), club.getId(),
                "That would be great! Also, our next session is Saturday 10am at DKP2 — same venue as the GDSC meetup.", null);
        Message g1m5 = saveMessage(group1.getId(), student.getId(),
                "Perfect, I'll be there. Should I bring my laptop?", null);
        Message g1m6 = saveMessage(group1.getId(), club.getId(),
                "Yes! We'll be doing live coding exercises on data structures.", null);
        Message g1m7 = saveMessage(group1.getId(), alumni.getId(),
                "I'm remote but I can join on video if you set up a screen share. Just ping me 5 min before.", null);

        markRead(g1m1, student.getId());
        markRead(g1m1, alumni.getId());
        markRead(g1m2, club.getId());
        markRead(g1m2, alumni.getId());
        markRead(g1m3, club.getId());
        markRead(g1m3, student.getId());
        markRead(g1m4, student.getId());
        markRead(g1m4, alumni.getId());
        markRead(g1m5, club.getId());

        log.info("Seeded group conversation: CS Study Group ({} messages).", 7);

        // ── DM: alumni ↔ employer (B2B networking) ──────────────────────────

        Conversation dm3 = saveConversation(ConversationType.DIRECT, null, alumni.getId());
        addMember(dm3, alumni.getId(),   MemberRole.ADMIN);
        addMember(dm3, employer.getId(), MemberRole.MEMBER);

        Message dm3m1 = saveMessage(dm3.getId(), employer.getId(),
                "Hi! Noticed your profile — impressive background at Petronas Digital. Are you open to senior roles?", null);
        Message dm3m2 = saveMessage(dm3.getId(), alumni.getId(),
                "Thanks for reaching out! Always open to interesting opportunities. What kind of role did you have in mind?", null);
        Message dm3m3 = saveMessage(dm3.getId(), employer.getId(),
                "We're looking for a Lead Backend Engineer. Full-stack experience would be a bonus. Can we schedule a 30-minute call?", null);
        Message dm3m4 = saveMessage(dm3.getId(), alumni.getId(),
                "Sure! I'm free Thursday afternoon or Friday morning. Let me know what works.", null);

        markRead(dm3m1, alumni.getId());
        markRead(dm3m2, employer.getId());
        markRead(dm3m3, alumni.getId());

        log.info("Seeded DM: alumni ↔ employer ({} messages).", 4);

        // ── Notifications (MESSAGE type) ─────────────────────────────────────

        saveNotification(student.getId(),  alumni.getId(),   NotificationType.MESSAGE, dm1.getId(), "conversation");
        saveNotification(alumni.getId(),   student.getId(),  NotificationType.MESSAGE, dm1.getId(), "conversation");
        saveNotification(student.getId(),  employer.getId(), NotificationType.MESSAGE, dm2.getId(), "conversation");
        saveNotification(employer.getId(), student.getId(),  NotificationType.MESSAGE, dm2.getId(), "conversation");
        saveNotification(student.getId(),  club.getId(),     NotificationType.MESSAGE, group1.getId(), "conversation");
        saveNotification(alumni.getId(),   club.getId(),     NotificationType.MESSAGE, group1.getId(), "conversation");
        saveNotification(alumni.getId(),   employer.getId(), NotificationType.MESSAGE, dm3.getId(), "conversation");
        saveNotification(employer.getId(), alumni.getId(),   NotificationType.MESSAGE, dm3.getId(), "conversation");

        log.info("Seeded {} MESSAGE notifications.", 8);
        log.info("Messaging seeding complete.");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Conversation saveConversation(ConversationType type, String name, Long createdBy) {
        Conversation c = new Conversation();
        c.setConvType(type);
        c.setName(name);
        c.setCreatedBy(createdBy);
        return conversationRepository.save(c);
    }

    private void addMember(Conversation conv, Long userId, MemberRole role) {
        ConversationMember m = new ConversationMember();
        m.setConversationId(conv.getId());
        m.setUserId(userId);
        m.setRole(role);
        memberRepository.save(m);
    }

    private Message saveMessage(Long convId, Long senderId, String content, Long replyToId) {
        Message m = new Message();
        m.setConversationId(convId);
        m.setSenderId(senderId);
        m.setContent(content);
        m.setMsgType(MessageType.TEXT);
        m.setReplyToId(replyToId);
        return messageRepository.save(m);
    }

    private void markRead(Message message, Long userId) {
        if (!messageReadRepository.existsByMessageIdAndUserId(message.getId(), userId)) {
            MessageRead r = new MessageRead();
            r.setMessageId(message.getId());
            r.setUserId(userId);
            messageReadRepository.save(r);
        }
    }

    private void saveNotification(Long userId, Long actorId, NotificationType type, Long targetId, String targetType) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setActorId(actorId);
        n.setNotifType(type);
        n.setTargetId(targetId);
        n.setTargetType(targetType);
        notificationRepository.save(n);
    }
}
