package com.unisphere.backend.commerce.services.dto.response;

/**
 * @param uploadUrl  short-lived presigned PUT URL the client uploads straight to
 * @param mediaKey   the bare key to send back as {@code portfolioImageKey} — never a resolved URL
 */
public record ServiceMediaPresignResponse(
        String uploadUrl,
        String mediaKey
) {}
