package com.unisphere.backend.commerce.jobs.dto.response;

/**
 * @param uploadUrl short-lived presigned PUT URL the client uploads straight to
 * @param mediaKey  the bare key to send back as {@code resumeKey} on apply — never a resolved URL
 */
public record JobApplicationMediaPresignResponse(
        String uploadUrl,
        String mediaKey
) {}
