package iuh.fit.userservice.dto.enums;

public enum UserStatusAction {
    BLOCK,
    UNBLOCK,
    DEACTIVATE,
    @Deprecated
    SOFT_DELETE,
    RESTORE
}
