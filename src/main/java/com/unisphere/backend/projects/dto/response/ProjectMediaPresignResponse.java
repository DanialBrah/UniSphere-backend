package com.unisphere.backend.projects.dto.response;

/**
 * @param uploadUrl short-lived presigned PUT URL the client uploads straight to
 * @param mediaKey  the bare key to send back as {@code coverImageKey} on create/update — never a
 *                  resolved URL
 */
public record ProjectMediaPresignResponse(
        String uploadUrl,
        String mediaKey
) {}
