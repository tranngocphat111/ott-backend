package iuh.fit.ottbackend.utils;

import org.springframework.stereotype.Component;

@Component
public class ValidationUtils {

    public boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }

    public boolean isValidPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }

        String phoneRegex = "^0[0-9]{9,10}$";
        return phone.matches(phoneRegex);
    }

    public boolean isValidPassword(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }

        boolean hasUppercase = password.matches(".*[A-Z].*");
        boolean hasLowercase = password.matches(".*[a-z].*");
        boolean hasNumber = password.matches(".*[0-9].*");

        return hasUppercase && hasLowercase && hasNumber;
    }

    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }

        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts[1];

        if (username.length() <= 3) {
            return username.charAt(0) + "***@" + domain;
        }

        return username.substring(0, 2) +
                "***" +
                username.substring(username.length() - 1) +
                "@" + domain;
    }

    public String sanitizeString(String input) {
        if (input == null) {
            return null;
        }

        return input
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll("\"", "&quot;")
                .replaceAll("'", "&#x27;")
                .replaceAll("/", "&#x2F;")
                .trim();
    }

}