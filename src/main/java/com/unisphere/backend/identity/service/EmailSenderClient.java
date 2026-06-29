package com.unisphere.backend.identity.service;

import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;

public interface EmailSenderClient {
    void send(CreateEmailOptions options) throws ResendException;
}
