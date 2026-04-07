package iuh.fit.notificationservice.service;

import iuh.fit.notificationservice.entity.EmailLog;
import iuh.fit.notificationservice.entity.enums.EmailStatus;
import iuh.fit.notificationservice.entity.enums.EmailType;
import iuh.fit.notificationservice.entity.enums.OtpType;
import iuh.fit.notificationservice.exception.AppException;
import iuh.fit.notificationservice.exception.ErrorCode;
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
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailLogRepository emailLogRepository;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.name}")
    private String appName;

    @Value("${app.website}")
    private String websiteUrl;

    @Value("${app.support-email}")
    private String supportEmail;

    @Value("${otp.expiry-minutes}")
    private int otpExpiryMinutes;

    public void sendOtpEmail(String toEmail, String toName, String otpCode,
                             OtpType otpType, String ipAddress, String location,
                             String userId) {

        log.info("Preparing to send OTP email to: {} | Type: {}", toEmail, otpType);

        String subject = getSubjectByOtpType(otpType);

        EmailLog emailLog = EmailLog.builder()
                .userId(userId)
                .emailTo(toEmail)
                .emailType(EmailType.OTP_VERIFICATION)
                .subject(subject)
                .templateName("email/otp-email")
                .status(EmailStatus.PENDING)
                .build();

        try {
            Context context = new Context();
            context.setVariable("appName", appName);
            context.setVariable("fullName", toName != null ? toName : "User");
            context.setVariable("otpCode", otpCode);
            context.setVariable("expiryMinutes", otpExpiryMinutes);
            context.setVariable("websiteUrl", websiteUrl);
            context.setVariable("supportEmail", supportEmail);
            context.setVariable("otpType", getOtpTypeName(otpType));
            context.setVariable("timestamp", LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss")));

            if (ipAddress != null) context.setVariable("ipAddress", ipAddress);
            if (location != null) context.setVariable("location", location);

            String htmlContent = templateEngine.process("email/otp-email", context);

            log.debug("OTP email template processed successfully, sending...");

            sendHtmlEmail(toEmail, subject, htmlContent);

            emailLog.setStatus(EmailStatus.SENT);
            emailLog.setSentAt(LocalDateTime.now());
            log.info("OTP email sent successfully to: {} | Type: {}", toEmail, otpType);

        } catch (Exception e) {
            emailLog.setStatus(EmailStatus.FAILED);
            emailLog.setErrorMessage(e.getMessage());
            emailLog.setFailedAt(LocalDateTime.now());
            log.error("Failed to send OTP email to: {} | Type: {}", toEmail, otpType, e);
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED, "Failed to send OTP email");
        } finally {
            emailLogRepository.save(emailLog);
        }
    }

    public void sendWelcomeEmail(String toEmail, String toName, String phone,
                                 boolean hasPassword, boolean hasGoogleLinked,
                                 String userId) {

        if (toEmail == null || toEmail.isBlank()) {
            log.warn("sendWelcomeEmail called with null/empty email, skipping. userId={}", userId);
            return;
        }

        log.info("Preparing welcome email for userId: {} | Email: {}", userId, toEmail);

        String subject = "Welcome to " + appName + "!";

        EmailLog emailLog = EmailLog.builder()
                .userId(userId)
                .emailTo(toEmail)
                .emailType(EmailType.WELCOME)
                .subject(subject)
                .templateName("email/welcome-email")
                .status(EmailStatus.PENDING)
                .build();

        try {
            Context context = new Context();
            context.setVariable("appName", appName);
            context.setVariable("fullName", toName);
            context.setVariable("email", toEmail);
            context.setVariable("phone", phone);
            context.setVariable("websiteUrl", websiteUrl);
            context.setVariable("supportEmail", supportEmail);
            context.setVariable("hasPassword", hasPassword);
            context.setVariable("hasGoogleLinked", hasGoogleLinked);

            String htmlContent = templateEngine.process("email/welcome-email", context);
            sendHtmlEmail(toEmail, subject, htmlContent);

            emailLog.setStatus(EmailStatus.SENT);
            emailLog.setSentAt(LocalDateTime.now());
            log.info("Welcome email sent successfully to: {}", toEmail);

        } catch (Exception e) {
            emailLog.setStatus(EmailStatus.FAILED);
            emailLog.setErrorMessage(e.getMessage());
            emailLog.setFailedAt(LocalDateTime.now());
            log.error("Failed to send welcome email to: {}", toEmail, e);
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED, "Failed to send welcome email");
        } finally {
            emailLogRepository.save(emailLog);
        }
    }

    public void sendAlertEmail(String toEmail, String toName, String alertType,
                               String ipAddress, String location, String deviceInfo,
                               String userId) {

        log.info("Preparing security alert email to: {} | AlertType: {}", toEmail, alertType);

        String subject = "Security Alert - " + alertType;

        EmailLog emailLog = EmailLog.builder()
                .userId(userId)
                .emailTo(toEmail)
                .emailType(EmailType.SECURITY_ALERT)
                .subject(subject)
                .templateName("email/alert-email")
                .status(EmailStatus.PENDING)
                .build();

        try {
            Context context = new Context();
            context.setVariable("appName", appName);
            context.setVariable("fullName", toName != null ? toName : "User");
            context.setVariable("alertType", alertType);
            context.setVariable("ipAddress", ipAddress);
            context.setVariable("location", location);
            context.setVariable("deviceInfo", deviceInfo);
            context.setVariable("websiteUrl", websiteUrl);
            context.setVariable("supportEmail", supportEmail);
            context.setVariable("timestamp", LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss")));

            String htmlContent = templateEngine.process("email/alert-email", context);
            sendHtmlEmail(toEmail, subject, htmlContent);

            emailLog.setStatus(EmailStatus.SENT);
            emailLog.setSentAt(LocalDateTime.now());
            log.info("Security alert email sent successfully to: {} | Type: {}", toEmail, alertType);

        } catch (Exception e) {
            emailLog.setStatus(EmailStatus.FAILED);
            emailLog.setErrorMessage(e.getMessage());
            emailLog.setFailedAt(LocalDateTime.now());
            log.error("Failed to send alert email to: {} | Type: {}", toEmail, alertType, e);
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED, "Failed to send alert email");
        } finally {
            emailLogRepository.save(emailLog);
        }
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        log.debug("Sending HTML email to: {} | Subject: {}", to, subject);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromEmail, fromName));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.debug("Email sent via SMTP successfully to: {}", to);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send HTML email to: {}", to, e);
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private String getSubjectByOtpType(OtpType otpType) {
        return switch (otpType) {
            case REGISTER -> "Verify Your Registration - " + appName;
            case TWO_FACTOR_AUTH -> "Your Login Verification Code - " + appName;
            case LOGIN_OTP_EMAIL -> "Your Login OTP Code - " + appName;
            case EMAIL_VERIFICATION -> "Verify Your Email - " + appName;
            case RESET_PASSWORD -> "Reset Your Password - " + appName;
            case CHANGE_PASSWORD -> "Change Your Password - " + appName;
            case CHANGE_EMAIL -> "Confirm Your New Email - " + appName;
            case LINK_PHONE -> "Link Phone Number - " + appName;
            case LINK_EMAIL -> "Link Email Address - " + appName;
            case CHANGE_PHONE -> "Change Phone Number - " + appName;
            case LINK_GOOGLE_ACCOUNT -> "Link Google Account - " + appName;
            case DELETE_ACCOUNT -> "Delete Your Account - " + appName;
            case ENABLE_TWO_FACTOR -> "Enable Two-Factor Authentication - " + appName;
            case DISABLE_TWO_FACTOR -> "Disable Two-Factor Authentication - " + appName;
        };
    }

    private String getOtpTypeName(OtpType otpType) {
        return switch (otpType) {
            case REGISTER -> "Registration";
            case TWO_FACTOR_AUTH -> "Two-Factor Authentication";
            case LOGIN_OTP_EMAIL -> "Email Login";
            case EMAIL_VERIFICATION -> "Email Verification";
            case RESET_PASSWORD -> "Password Reset";
            case CHANGE_PASSWORD -> "Password Change";
            case CHANGE_EMAIL -> "Email Change";
            case LINK_PHONE -> "Phone Linking";
            case LINK_EMAIL -> "Email Linking";
            case CHANGE_PHONE -> "Phone Change";
            case LINK_GOOGLE_ACCOUNT -> "Google Account Linking";
            case DELETE_ACCOUNT -> "Account Deletion";
            case ENABLE_TWO_FACTOR -> "Enable 2FA";
            case DISABLE_TWO_FACTOR -> "Disable 2FA";
        };
    }
}