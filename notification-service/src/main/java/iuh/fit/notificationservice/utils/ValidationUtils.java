package iuh.fit.notificationservice.utils;

import org.springframework.stereotype.Component;

@Component
public class ValidationUtils {

    public String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) return phone;

        String cleanPhone = phone.replaceAll("[^0-9+]", "");
        int length = cleanPhone.length();
        if (length <= 6) return phone;

        String prefix = cleanPhone.substring(0, 3);
        String suffix = cleanPhone.substring(length - 3);
        String asterisks = "*".repeat(length - 6);
        return prefix + asterisks + suffix;
    }

    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) return false;
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }

    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;

        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts[1];

        if (username.length() <= 3) {
            return username.charAt(0) + "***@" + domain;
        }

        return username.substring(0, 2) + "***" + username.substring(username.length() - 1) + "@" + domain;
    }

    public String sanitizeString(String input) {
        if (input == null) return null;
        return input.replaceAll("<", "&lt;").replaceAll(">", "&gt;").trim();
    }
}