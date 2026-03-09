package iuh.fit.userservice.entity.enums;

import lombok.Getter;

@Getter
public enum DeviceType {
    MOBILE("mobile"), TABLET("tablet"), TV("tv"), DESKTOP("desktop"), UNKNOWN("unknown");
    private final String value;

    DeviceType(String value) {
        this.value = value;
    }
}