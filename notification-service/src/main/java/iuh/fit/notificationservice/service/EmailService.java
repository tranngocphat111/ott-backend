package iuh.fit.notificationservice.service;

import iuh.fit.notificationservice.entity.EmailLog;
import iuh.fit.notificationservice.entity.enums.EmailStatus;
import iuh.fit.notificationservice.entity.enums.EmailType;
import iuh.fit.notificationservice.repository.EmailLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final EmailLogRepository emailLogRepository;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.mail.from-name}")
    private String fromName;

    public void sendOtpEmail(String toEmail, String toName, String otpCode,
                             String otpType, String ipAddress, String location,
                             String userId) {
        EmailLog log = EmailLog.builder()
                .userId(userId)
                .emailTo(toEmail)
                .emailType(EmailType.OTP_VERIFICATION)
                .subject("Your OTP Code")
                .templateName("otp-email")
                .status(EmailStatus.PENDING)
                .build();

        try {
            Context context = new Context();
            context.setVariable("name", toName);
            context.setVariable("otpCode", otpCode);
            context.setVariable("otpType", otpType);
            context.setVariable("ipAddress", ipAddress);
            context.setVariable("location", location);
            context.setVariable("expiryMinutes", 5);

            String content = templateEngine.process("otp-email", context);
            sendHtmlEmail(toEmail, "Your OTP Code", content);

            log.setStatus(EmailStatus.SENT);
            log.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            log.setStatus(EmailStatus.FAILED);
            log.setErrorMessage(e.getMessage());
            log.setFailedAt(LocalDateTime.now());
            throw new RuntimeException("Failed to send OTP email", e);
        } finally {
            emailLogRepository.save(log);
        }
    }

    public void sendWelcomeEmail(String toEmail, String toName, String userId) {
        EmailLog log = EmailLog.builder()
                .userId(userId)
                .emailTo(toEmail)
                .emailType(EmailType.WELCOME)
                .subject("Welcome to OTT Platform!")
                .templateName("welcome-email")
                .status(EmailStatus.PENDING)
                .build();

        try {
            Context context = new Context();
            context.setVariable("name", toName);

            String content = templateEngine.process("welcome-email", context);
            sendHtmlEmail(toEmail, "Welcome to OTT Platform!", content);

            log.setStatus(EmailStatus.SENT);
            log.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            log.setStatus(EmailStatus.FAILED);
            log.setErrorMessage(e.getMessage());
            log.setFailedAt(LocalDateTime.now());
            throw new RuntimeException("Failed to send welcome email", e);
        } finally {
            emailLogRepository.save(log);
        }
    }

    public void sendAlertEmail(String toEmail, String toName, String alertType,
                               String ipAddress, String location,
                               String deviceInfo, String userId) {
        EmailLog log = EmailLog.builder()
                .userId(userId)
                .emailTo(toEmail)
                .emailType(EmailType.SECURITY_ALERT)
                .subject("Security Alert")
                .templateName("alert-email")
                .status(EmailStatus.PENDING)
                .build();

        try {
            Context context = new Context();
            context.setVariable("name", toName);
            context.setVariable("alertType", alertType);
            context.setVariable("ipAddress", ipAddress);
            context.setVariable("location", location);
            context.setVariable("deviceInfo", deviceInfo);

            String content = templateEngine.process("alert-email", context);
            sendHtmlEmail(toEmail, "Security Alert - " + alertType, content);

            log.setStatus(EmailStatus.SENT);
            log.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            log.setStatus(EmailStatus.FAILED);
            log.setErrorMessage(e.getMessage());
            log.setFailedAt(LocalDateTime.now());
            throw new RuntimeException("Failed to send alert email", e);
        } finally {
            emailLogRepository.save(log);
        }
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(new InternetAddress(fromEmail, fromName));
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }
}
