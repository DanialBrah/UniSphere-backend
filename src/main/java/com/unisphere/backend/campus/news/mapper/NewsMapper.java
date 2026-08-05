package com.unisphere.backend.campus.news.mapper;

import com.unisphere.backend.campus.news.dto.response.NewsMediaResponse;
import com.unisphere.backend.campus.news.entity.NewsMedia;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = MediaUrlResolver.class)
public interface NewsMapper {

    /**
     * The entity holds a bare key; the response carries a presigned URL minted per read. The
     * response field keeps the name mediaUrl because that is what clients already consume.
     */
    @Mapping(target = "mediaUrl", source = "mediaKey", qualifiedByName = "toViewableUrl")
    NewsMediaResponse toMediaResponse(NewsMedia media);
}
