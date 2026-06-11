package com.unisphere.backend.social.messaging.validation;

import com.unisphere.backend.social.messaging.dto.request.SendMessageRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MessagePayloadValidator implements ConstraintValidator<ValidMessagePayload, SendMessageRequest> {

    @Override
    public boolean isValid(SendMessageRequest req, ConstraintValidatorContext context) {
        if (req == null) return true;
        boolean hasContent = req.content() != null && !req.content().isBlank();
        boolean hasMedia = req.mediaUrl() != null && !req.mediaUrl().isBlank();
        return hasContent || hasMedia;
    }
}
