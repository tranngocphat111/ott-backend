package iuh.fit.ottbackend.service;

import iuh.fit.ottbackend.entity.User;
import iuh.fit.ottbackend.entity.enums.OtpType;
import iuh.fit.ottbackend.exception.AppException;
import iuh.fit.ottbackend.exception.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

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

    public void sendOtpEmail(String to, String fullName, String otpCode, OtpType otpType,
                             String ipAddress, String location) {

        String subject = getSubjectByOtpType(otpType);
        String templateName = "email/otp-email";

        Context context = new Context();
        context.setVariable("appName", appName);
        context.setVariable("fullName", fullName != null ? fullName : "User");
        context.setVariable("otpCode", otpCode);
        context.setVariable("expiryMinutes", otpExpiryMinutes);
        context.setVariable("websiteUrl", websiteUrl);
        context.setVariable("supportEmail", supportEmail);
        context.setVariable("otpType", getOtpTypeName(otpType));
        context.setVariable("timestamp", LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss")));

        if (ipAddress != null) {
            context.setVariable("ipAddress", ipAddress);
        }
        if (location != null) {
            context.setVariable("location", location);
        }

        String htmlContent = templateEngine.process(templateName, context);

        sendHtmlEmail(to, subject, htmlContent);
    }

    public void sendWelcomeEmail(User user) {
        String subject = "Welcome to " + appName + "!";
        String templateName = "email/welcome-email";

        Context context = new Context();
        context.setVariable("appName", appName);
        context.setVariable("fullName", user.getFullName());
        context.setVariable("email", user.getEmail());
        context.setVariable("phone", user.getPhone());
        context.setVariable("websiteUrl", websiteUrl);
        context.setVariable("supportEmail", supportEmail);
        context.setVariable("hasPassword", user.getPasswordHash() != null);
        context.setVariable("hasGoogleLinked", user.getGoogleId() != null);

        String htmlContent = templateEngine.process(templateName, context);

        sendHtmlEmail(user.getEmail(), subject, htmlContent);
    }

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
        } catch (Exception e) {
            throw new AppException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private String getSubjectByOtpType(OtpType otpType) {
        return switch (otpType) {
            case REGISTER -> "Verify Your Registration - " + appName;
            case TWO_FACTOR_AUTH -> "Your Login Verification Code - " + appName;
            case LOGIN_OTP_EMAIL -> null;
            case EMAIL_VERIFICATION -> null;
            case RESET_PASSWORD -> "Reset Your Password - " + appName;
            case CHANGE_PASSWORD -> null;
            case CHANGE_EMAIL -> "Confirm Your New Email - " + appName;
            case LINK_PHONE -> "Link Phone Number - " + appName;
            case LINK_EMAIL -> "Link Email Address - " + appName;
            case CHANGE_PHONE -> null;
            case LINK_GOOGLE_ACCOUNT -> null;
            case DELETE_ACCOUNT -> null;
            case ENABLE_TWO_FACTOR -> null;
            case DISABLE_TWO_FACTOR -> null;
        };
    }

    private String getOtpTypeName(OtpType otpType) {
        return switch (otpType) {
            case REGISTER -> "Registration";
            case TWO_FACTOR_AUTH -> "Two-Factor Authentication";
            case LOGIN_OTP_EMAIL -> null;
            case EMAIL_VERIFICATION -> null;
            case RESET_PASSWORD -> "Password Reset";
            case CHANGE_PASSWORD -> null;
            case CHANGE_EMAIL -> "Email Change";
            case LINK_PHONE -> "Phone Linking";
            case LINK_EMAIL -> "Email Linking";
            case CHANGE_PHONE -> null;
            case LINK_GOOGLE_ACCOUNT -> null;
            case DELETE_ACCOUNT -> null;
            case ENABLE_TWO_FACTOR -> null;
            case DISABLE_TWO_FACTOR -> null;
        };
    }
}