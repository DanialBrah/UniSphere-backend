package com.unisphere.backend.social.community.service;

import com.unisphere.backend.common.exception.CommunityNotFoundException;
import com.unisphere.backend.identity.entity.User;
import com.unisphere.backend.social.community.dto.response.ChatAccessResponse;
import com.unisphere.backend.social.messaging.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Community chat is not reimplemented — a community's single conversation is auto-provisioned in
 * CommunityService.createCommunity and its membership mirrored by CommunityMembershipService.
 * This service only hands the frontend the conversationId so it can reuse the existing message
 * endpoints and STOMP topics unmodified.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommunityChatService {

    private final ConversationRepository conversationRepository;
    private final CommunityAccessService communityAccessService;

    public ChatAccessResponse getChatAccess(Long communityId, User currentUser) {
        communityAccessService.assertMember(communityId, currentUser.getId());
        Long conversationId = conversationRepository.findByCommunityId(communityId)
                .orElseThrow(() -> new CommunityNotFoundException(communityId))
                .getId();
        return new ChatAccessResponse(conversationId);
    }
}
