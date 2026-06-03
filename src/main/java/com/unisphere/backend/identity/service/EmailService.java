package com.unisphere.backend.identity.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Async
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject("Reset your UniSphere password");
            helper.setText(buildResetEmailHtml(resetLink), true);
            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (MessagingException e) {
            // Log but never rethrow — the forgot-password response must not reveal
            // whether an email exists or whether SMTP delivery succeeded.
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildResetEmailHtml(String resetLink) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family:sans-serif;max-width:600px;margin:auto;padding:24px">
                  <h2 style="color:#2563eb">Reset your UniSphere password</h2>
                  <p>We received a request to reset your password. Click the button below to
                     choose a new password. This link expires in <strong>15 minutes</strong>.</p>
                  <p style="text-align:center;margin:32px 0">
                    <a href="%s"
                       style="background:#2563eb;color:#fff;padding:12px 24px;border-radius:6px;
                              text-decoration:none;font-weight:bold">
                      Reset Password
                    </a>
                  </p>
                  <p style="color:#6b7280;font-size:13px">
                    If you did not request this, you can safely ignore this email.
                    Your password will not be changed.
                  </p>
                  <hr style="border:none;border-top:1px solid #e5e7eb;margin-top:32px">
                  <p style="color:#9ca3af;font-size:12px;text-align:center">
                    &copy; UniSphere. All rights reserved.
                  </p>
                </body>
                </html>
                """.formatted(resetLink);
    }
}
