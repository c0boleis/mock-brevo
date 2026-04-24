package org.enoria.mockbrevo.smtp;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import org.enoria.mockbrevo.brevo.dto.SendSmtpEmailRequest;
import org.enoria.mockbrevo.config.MockBrevoProperties;
import org.enoria.mockbrevo.domain.SentEmailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SmtpForwarder {

    private static final Logger log = LoggerFactory.getLogger(SmtpForwarder.class);

    private final MockBrevoProperties properties;
    private final SentEmailRepository sentEmails;

    public SmtpForwarder(MockBrevoProperties properties, SentEmailRepository sentEmails) {
        this.properties = properties;
        this.sentEmails = sentEmails;
    }

    @Async
    @Transactional
    public void forward(SendSmtpEmailRequest req, String messageId, Long sentEmailId) {
        MockBrevoProperties.Smtp smtp = properties.getSmtp();
        if (!smtp.isEnabled()) return;
        if (req == null || req.to() == null || req.to().isEmpty()) return;

        JavaMailSenderImpl sender = buildSender(smtp);
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            String fromEmail = req.sender() != null && req.sender().email() != null
                    ? req.sender().email()
                    : "no-reply@mock-brevo.local";
            String fromName = req.sender() != null ? req.sender().name() : null;
            if (fromName != null && !fromName.isBlank()) {
                helper.setFrom(new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name()));
            } else {
                helper.setFrom(fromEmail);
            }

            helper.setTo(toArray(req.to()));
            if (req.cc() != null && !req.cc().isEmpty()) helper.setCc(toArray(req.cc()));
            if (req.bcc() != null && !req.bcc().isEmpty()) helper.setBcc(toArray(req.bcc()));
            if (req.replyTo() != null && req.replyTo().email() != null) {
                helper.setReplyTo(req.replyTo().email());
            }

            helper.setSubject(req.subject() != null ? req.subject() : "(no subject)");

            boolean html = req.htmlContent() != null && !req.htmlContent().isBlank();
            if (html && req.textContent() != null && !req.textContent().isBlank()) {
                helper.setText(req.textContent(), req.htmlContent());
            } else if (html) {
                helper.setText(req.htmlContent(), true);
            } else {
                helper.setText(req.textContent() != null ? req.textContent() : "");
            }

            // Brevo messageId for cross-referencing between mock-brevo UI and SMTP catcher
            message.setHeader("Message-ID", messageId);
            message.setHeader("X-Mock-Brevo-Message-Id", messageId);

            sender.send(message);
            log.info("SMTP forward OK: messageId={} to={}", messageId, req.to().get(0).email());
            if (sentEmailId != null) {
                sentEmails.findById(sentEmailId).ifPresent(e -> {
                    e.setSmtpForwardedAt(Instant.now());
                    e.setSmtpForwardError(null);
                    sentEmails.save(e);
                });
            }
        } catch (Exception e) {
            log.warn("SMTP forward failed: {} ({}:{}) — {}",
                    e.getClass().getSimpleName(), smtp.getHost(), smtp.getPort(), e.getMessage());
            if (sentEmailId != null) {
                sentEmails.findById(sentEmailId).ifPresent(row -> {
                    String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                    row.setSmtpForwardError(err.length() > 500 ? err.substring(0, 500) : err);
                    sentEmails.save(row);
                });
            }
        }
    }

    private String[] toArray(List<SendSmtpEmailRequest.EmailAddress> addresses) {
        return addresses.stream()
                .map(SendSmtpEmailRequest.EmailAddress::email)
                .filter(e -> e != null && !e.isBlank())
                .toArray(String[]::new);
    }

    private JavaMailSenderImpl buildSender(MockBrevoProperties.Smtp smtp) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.getHost());
        sender.setPort(smtp.getPort());
        if (!smtp.getUsername().isBlank()) {
            sender.setUsername(smtp.getUsername());
            sender.setPassword(smtp.getPassword());
        }
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", smtp.getUsername().isBlank() ? "false" : "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(smtp.isStarttls()));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        return sender;
    }
}
