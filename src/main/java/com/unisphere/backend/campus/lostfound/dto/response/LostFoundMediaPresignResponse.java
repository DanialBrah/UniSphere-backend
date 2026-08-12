package com.unisphere.backend.campus.lostfound.dto.response;

/**
 * @param uploadUrl short-lived presigned PUT URL the client uploads straight to
 * @param mediaKey  the bare key to send back on create/update — never a resolved URL
 */
public record LostFoundMediaPresignResponse(
        String uploadUrl,
        String mediaKey
) {}
