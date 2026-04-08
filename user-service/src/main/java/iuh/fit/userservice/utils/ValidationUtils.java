package iuh.fit.userservice.utils;

import org.springframework.stereotype.Component;

@Component
public class ValidationUtils {

    public String normalizePhone(String phone) {
        if (phone == null) return null;
        String normalized = phone.replaceAll("[^0-9+]", "").trim();
        if (normalized.startsWith("+84")) {
            normalized = "0" + normalized.substring(3);
        } else if (normalized.startsWith("84") && normalized.length() >= 11) {
            normalized = "0" + normalized.substring(2);
        }
        return normalized;
    }

    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) return false;
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    public boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) return false;
        String normalized = normalizePhone(phone);
        return normalized != null && normalized.matches("^0[0-9]{9,10}$");
    }

    public boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) return false;
        return password.matches(".*[A-Z].*") && password.matches(".*[a-z].*") && password.matches(".*[0-9].*");
    }

    public String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) return phone;
        String clean = phone.replaceAll("[^0-9+]", "");
        int len = clean.length();
        if (len <= 6) return phone;
        return clean.substring(0, 3) + "*".repeat(len - 6) + clean.substring(len - 3);
    }

    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        String u = parts[0], d = parts[1];
        if (u.length() <= 3) return u.charAt(0) + "***@" + d;
        return u.substring(0, 2) + "***" + u.substring(u.length() - 1) + "@" + d;
    }

    public String sanitizeString(String input) {
        if (input == null) return null;
        return input.replaceAll("<", "&lt;").replaceAll(">", "&gt;")
                .replaceAll("\"", "&quot;").replaceAll("'", "&#x27;")
                .replaceAll("/", "&#x2F;").trim();
    }
}