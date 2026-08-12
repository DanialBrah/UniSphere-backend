package com.unisphere.backend.campus.lostfound.mapper;

import com.unisphere.backend.campus.lostfound.dto.response.LostFoundMediaResponse;
import com.unisphere.backend.campus.lostfound.entity.LostFoundItemMedia;
import com.unisphere.backend.common.storage.MediaUrlResolver;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Media only — the rest of the mapping is viewer-dependent (see
 * {@code LostFoundAccessService.locationViewFor}) and therefore lives in hand-written methods in
 * {@code LostFoundService}, where the privacy guard is applied. Mirrors {@code NewsMapper}.
 */
@Mapper(componentModel = "spring", uses = MediaUrlResolver.class)
public interface LostFoundMapper {

    /**
     * The entity holds a bare key; the response carries a presigned URL minted per read. The
     * response field keeps the name mediaUrl because that is what clients already consume.
     */
    @Mapping(target = "mediaUrl", source = "mediaKey", qualifiedByName = "toViewableUrl")
    LostFoundMediaResponse toMediaResponse(LostFoundItemMedia media);
}
