package com.unisphere.backend.social.messaging.mapper;

import com.unisphere.backend.social.messaging.dto.response.MessageResponse;
import com.unisphere.backend.social.messaging.entity.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MessageMapper {

    @Mapping(target = "senderName", ignore = true)
    @Mapping(target = "senderAvatar", ignore = true)
    MessageResponse toResponse(Message message);
}
