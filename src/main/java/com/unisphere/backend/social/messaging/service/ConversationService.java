package com.unisphere.backend.social.messaging.service;

import com.unisphere.backend.common.exception.ConversationNotFoundException;
import com.unisphere.backend.common.exception.NotConversationMemberException;
import com.unisphere.backend.common.exception.UnauthorizedActionException;
import com.unisphere.backend.identity.entity.*;
import com.unisphere.backend.identity.repository.UserRepository;
import com.unisphere.backend.social.messaging.dto.request.AddMemberRequest;
import com.unisphere.backend.social.messaging.dto.request.CreateConversationRequest;
import com.unisphere.backend.social.messaging.dto.response.ConversationResponse;
import com.unisphere.backend.social.messaging.dto.response.MemberSummary;
import com.unisphere.backend.social.messaging.dto.response.MessageResponse;
import com.unisphere.backend.social.messaging.entity.Conversation;
import com.unisphere.backend.social.messaging.entity.ConversationMember;
import com.unisphere.backend.social.messaging.entity.Message;
import com.unisphere.backend.social.messaging.enums.ConversationType;
import com.unisphere.backend.social.messaging.enums.MemberRole;
import com.unisphere.backend.social.messaging.repository.ConversationMemberRepository;
import com.unisphere.backend.social.messaging.repository.ConversationRepository;
import com.unisphere.backend.social.messaging.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public ConversationResponse createConversation(CreateConversationRequest req, User currentUser) {
        if (req.type() == ConversationType.DIRECT) {
            if (req.participantIds().size() != 1) {
                throw new IllegalArgumentException("Direct conversation requires exactly one participant");
            }
            Long otherId = req.participantIds().get(0);
            if (otherId.equals(currentUser.getId())) {
                throw new IllegalArgumentException("Cannot create a direct conversation with yourself");
            }
            return conversationRepository
                    .findDirectConversation(currentUser.getId(), otherId)
                    .map(existing -> toResponse(existing, currentUser.getId()))
                    .orElseGet(() -> createNewConversation(req, currentUser));
        }
        return createNewConversation(req, currentUser);
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> getInbox(User currentUser, Pageable pageable) {
        return conversationRepository
                .findAllByMemberId(currentUser.getId(), pageable)
                .map(c -> toResponse(c, currentUser.getId()));
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(Long convId, User currentUser) {
        Conversation conv = conversationRepository.findById(convId)
                .orElseThrow(() -> new ConversationNotFoundException(convId));
        assertMembership(convId, currentUser.getId());
        return toResponse(conv, currentUser.getId());
    }

    @Transactional(readOnly = true)
    public List<MemberSummary> getMembers(Long convId, User currentUser) {
        conversationRepository.findById(convId)
                .orElseThrow(() -> new ConversationNotFoundException(convId));
        assertMembership(convId, currentUser.getId());
        return memberRepository.findByConversationId(convId).stream()
                .map(m -> {
                    User u = userRepository.findById(m.getUserId()).orElse(null);
                    return toMemberSummary(m, u);
                })
                .toList();
    }

    public MemberSummary addMember(Long convId, AddMemberRequest req, User currentUser) {
        Conversation conv = conversationRepository.findById(convId)
                .orElseThrow(() -> new ConversationNotFoundException(convId));
        if (conv.getConvType() == ConversationType.DIRECT) {
            throw new IllegalArgumentException("Cannot add members to a direct conversation");
        }
        assertAdmin(convId, currentUser.getId());

        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + req.userId()));

        if (memberRepository.existsByConversationIdAndUserId(convId, req.userId())) {
            throw new IllegalArgumentException("User is already a member of this conversation");
        }

        ConversationMember member = new ConversationMember();
        member.setConversationId(convId);
        member.setUserId(req.userId());
        member.setRole(MemberRole.MEMBER);
        memberRepository.save(member);

        return toMemberSummary(member, user);
    }

    public MemberSummary promoteMember(Long convId, Long targetUserId, User currentUser) {
        conversationRepository.findById(convId)
                .orElseThrow(() -> new ConversationNotFoundException(convId));
        assertAdmin(convId, currentUser.getId());
        ConversationMember member = memberRepository.findByConversationIdAndUserId(convId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User " + targetUserId + " is not a member of this conversation"));
        if (member.getRole() == MemberRole.ADMIN) {
            throw new IllegalArgumentException("User is already an admin");
        }
        member.setRole(MemberRole.ADMIN);
        memberRepository.save(member);
        User user = userRepository.findById(targetUserId).orElse(null);
        return toMemberSummary(member, user);
    }

    public void removeMember(Long convId, Long targetUserId, User currentUser) {
        boolean isSelf = currentUser.getId().equals(targetUserId);
        if (!isSelf) {
            assertAdmin(convId, currentUser.getId());
        } else {
            assertMembership(convId, currentUser.getId());
        }
        memberRepository.deleteByConversationIdAndUserId(convId, targetUserId);
    }

    public void deleteConversation(Long convId, User currentUser) {
        Conversation conv = conversationRepository.findById(convId)
                .orElseThrow(() -> new ConversationNotFoundException(convId));
        if (conv.getConvType() == ConversationType.DIRECT) {
            assertMembership(convId, currentUser.getId());
        } else {
            assertAdmin(convId, currentUser.getId());
        }
        conv.setDeletedAt(java.time.LocalDateTime.now());
        conversationRepository.save(conv);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ConversationResponse createNewConversation(CreateConversationRequest req, User currentUser) {
        Conversation conv = new Conversation();
        conv.setConvType(req.type());
        conv.setName(req.name());
        conv.setCreatedBy(currentUser.getId());
        conversationRepository.save(conv);

        // Deduplicate participants while preserving order
        List<Long> allParticipants = new ArrayList<>();
        allParticipants.add(currentUser.getId());
        for (Long userId : req.participantIds()) {
            if (!allParticipants.contains(userId)) {
                allParticipants.add(userId);
            }
        }

        for (Long userId : allParticipants) {
            ConversationMember member = new ConversationMember();
            member.setConversationId(conv.getId());
            member.setUserId(userId);
            member.setRole(userId.equals(currentUser.getId()) ? MemberRole.ADMIN : MemberRole.MEMBER);
            memberRepository.save(member);
        }

        return toResponse(conv, currentUser.getId());
    }

    ConversationResponse toResponse(Conversation conv, Long viewerId) {
        List<ConversationMember> members = memberRepository.findByConversationId(conv.getId());
        List<MemberSummary> memberSummaries = members.stream()
                .map(m -> {
                    User u = userRepository.findById(m.getUserId()).orElse(null);
                    return toMemberSummary(m, u);
                })
                .toList();

        Message lastMsg = messageRepository
                .findTopByConversationIdOrderByCreatedAtDesc(conv.getId())
                .orElse(null);
        MessageResponse lastMessageResponse = lastMsg == null ? null : toMessageResponse(lastMsg);

        return new ConversationResponse(
                conv.getId(),
                conv.getConvType(),
                conv.getName(),
                memberSummaries,
                lastMessageResponse,
                conv.getCreatedAt()
        );
    }

    private MemberSummary toMemberSummary(ConversationMember member, User user) {
        if (user == null) {
            return new MemberSummary(member.getUserId(), "Unknown", null, member.getRole());
        }
        return new MemberSummary(user.getId(), resolveDisplayName(user), user.getAvatarUrl(), member.getRole());
    }

    private MessageResponse toMessageResponse(Message msg) {
        User sender = userRepository.findById(msg.getSenderId()).orElse(null);
        String name = sender == null ? "Unknown" : resolveDisplayName(sender);
        String avatar = sender == null ? null : sender.getAvatarUrl();
        return new MessageResponse(
                msg.getId(), msg.getConversationId(), msg.getSenderId(),
                name, avatar, msg.getContent(), msg.getMsgType(),
                msg.getMediaUrl(), msg.getReplyToId(), msg.getCreatedAt()
        );
    }

    private void assertMembership(Long convId, Long userId) {
        if (!memberRepository.existsByConversationIdAndUserId(convId, userId)) {
            throw new NotConversationMemberException();
        }
    }

    private void assertAdmin(Long convId, Long userId) {
        memberRepository.findByConversationIdAndUserId(convId, userId)
                .filter(m -> m.getRole() == MemberRole.ADMIN)
                .orElseThrow(() -> new UnauthorizedActionException("Only conversation admins can perform this action"));
    }

    static String resolveDisplayName(User user) {
        if (user instanceof Student s) return s.getFullName();
        if (user instanceof Alumni a)  return a.getFullName();
        if (user instanceof Admin a)   return a.getFullName();
        if (user instanceof Employer e) return e.getCompanyName();
        if (user instanceof University u) return u.getName();
        if (user instanceof Club c)    return c.getName();
        return user.getEmail();
    }
}
