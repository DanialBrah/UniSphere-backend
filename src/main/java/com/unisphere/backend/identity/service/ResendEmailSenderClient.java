package com.unisphere.backend.identity.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!ci")
public class ResendEmailSenderClient implements EmailSenderClient {

    private final Resend resend;

    public ResendEmailSenderClient(@Value("${resend.api-key}") String apiKey) {
        this.resend = new Resend(apiKey);
    }

    @Override
    public void send(CreateEmailOptions options) throws ResendException {
        resend.emails().send(options);
    }
}
