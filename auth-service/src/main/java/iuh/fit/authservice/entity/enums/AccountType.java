package iuh.fit.authservice.entity.enums;

import lombok.Getter;

@Getter
public enum AccountType {
    USER("user"), OA("oa"), ADMIN("admin");
    private final String value;

    AccountType(String value) {
        this.value = value;
    }
}