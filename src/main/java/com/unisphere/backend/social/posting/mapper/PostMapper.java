package com.unisphere.backend.social.posting.mapper;

import com.unisphere.backend.common.storage.MediaUrlResolver;
import com.unisphere.backend.social.posting.dto.response.PostMediaResponse;
import com.unisphere.backend.social.posting.entity.PostMedia;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = MediaUrlResolver.class)
public interface PostMapper {

    /**
     * The entity holds a bare key; the response carries a presigned URL minted per read. The
     * response field keeps the name mediaUrl because that is what clients already consume.
     */
    @Mapping(target = "mediaUrl", source = "mediaKey", qualifiedByName = "toViewableUrl")
    PostMediaResponse toMediaResponse(PostMedia media);
}
