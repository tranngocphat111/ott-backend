package iuh.fit.userservice.utils;

import iuh.fit.userservice.entity.User;
import iuh.fit.userservice.exception.AppException;
import iuh.fit.userservice.exception.ErrorCode;
import iuh.fit.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserValidationUtil {

    public final UserRepository userRepository;

    public void validateUserStatus(User user) {
        if (user.getDeletedAt() != null) throw new AppException(ErrorCode.ACCOUNT_DELETED);
        if (!user.getIsActive()) throw new AppException(ErrorCode.USER_NOT_ACTIVE);

        if (user.getIsBlocked()) {
            if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now())) {
                throw new AppException(ErrorCode.USER_BLOCKED);
            }
            user.setIsBlocked(false);
            user.setBlockedUntil(null);
            user.setBlockedReason(null);
            userRepository.save(user);
            log.info("User {} auto-unblocked", user.getId());
        }
    }

    public User getUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        validateUserStatus(user);
        return user;
    }

    public User findUserByPhoneOrEmail(String phone, String email) {
        User user;
        if (phone != null) {
            user = userRepository.findByPhone(phone)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        } else {
            user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        }
        validateUserStatus(user);
        return user;
    }

    public boolean hasPassword(User user) {
        return user.getPasswordHash() != null;
    }

    public void requirePassword(User user) {
        if (!hasPassword(user)) throw new AppException(ErrorCode.PASSWORD_NOT_SET);
    }
}