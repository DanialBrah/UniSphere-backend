package com.unisphere.backend.social.posting.mapper;

import com.unisphere.backend.social.posting.dto.response.PostMediaResponse;
import com.unisphere.backend.social.posting.entity.PostMedia;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PostMapper {

    PostMediaResponse toMediaResponse(PostMedia media);
}
