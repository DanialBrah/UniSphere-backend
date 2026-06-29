package com.unisphere.backend.identity.service;

import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("ci")
public class NoopEmailSenderClient implements EmailSenderClient {

    @Override
    public void send(CreateEmailOptions options) {
        log.info("[ci] Skipping real email send to {} — subject: {}", options.getTo(), options.getSubject());
    }
}
