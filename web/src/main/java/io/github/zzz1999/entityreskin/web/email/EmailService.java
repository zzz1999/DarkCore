package io.github.zzz1999.entityreskin.web.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends verification codes via SMTP (163 mail in production). In dev mode (default) or when no
 * SMTP credentials are configured, the code is logged instead of sent, so the application starts
 * and remains usable without configured credentials.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean devMode;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${entityreskin.email.dev-mode:true}") boolean devMode,
                        @Value("${spring.mail.username:}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.devMode = devMode;
        this.from = from;
    }

    public void sendVerificationCode(String email, String code) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (devMode || from == null || from.isBlank() || sender == null) {
            log.warn("[DEV] verification code for {} is {}", email, code);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("EntityReskin email verification code");
        message.setText("Your verification code is: " + code + ". Valid for 10 minutes. If this was not you, please ignore this email.");
        sender.send(message);
        log.info("verification code emailed to {}", email);
    }

    public void sendLowBalanceAlert(String email, long balanceBytes, long thresholdBytes) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (devMode || from == null || from.isBlank() || sender == null) {
            log.warn("[DEV] low-balance alert for {}: balance {} below threshold {}",
                    email, balanceBytes, thresholdBytes);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("EntityReskin balance alert");
        message.setText("Your traffic balance is below the configured threshold (about " + balanceBytes + " bytes remaining, threshold "
                + thresholdBytes + " bytes). Please top up to avoid interrupting resource loading for players.");
        sender.send(message);
        log.info("low-balance alert emailed to {}", email);
    }

    public void sendPasswordResetCode(String email, String code) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (devMode || from == null || from.isBlank() || sender == null) {
            log.warn("[DEV] password reset code for {} is {}", email, code);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("EntityReskin password reset");
        message.setText("Your password reset code is: " + code + ". Valid for 10 minutes. If this was not you, please ignore this email.");
        sender.send(message);
        log.info("password reset code emailed to {}", email);
    }
}
