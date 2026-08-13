package com.unisphere.backend.commerce.events.dto.response;

/**
 * @param uploadUrl short-lived presigned PUT URL the client uploads straight to
 * @param mediaKey  the bare key to send back as {@code coverImageKey} on create/update — never a resolved URL
 */
public record EventMediaPresignResponse(
        String uploadUrl,
        String mediaKey
) {}
