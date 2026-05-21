package iuh.fit.authservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Common
    UNCATEGORIZED_EXCEPTION(9999, "Đã có lỗi hệ thống xảy ra. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Khóa không hợp lệ.", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(1006, "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn.", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1007, "Bạn không có quyền thực hiện hành động này.", HttpStatus.FORBIDDEN),
    INVALID_REQUEST(1008, "Yêu cầu không hợp lệ. Vui lòng kiểm tra lại dữ liệu.", HttpStatus.BAD_REQUEST),

    // User
    USER_NOT_EXISTED(1002, "Tài khoản không tồn tại.", HttpStatus.NOT_FOUND),
    USER_NOT_ACTIVE(1003, "Tài khoản chưa được kích hoạt.", HttpStatus.FORBIDDEN),
    USER_BLOCKED(1004, "Tài khoản của bạn đã bị khóa tạm thời.", HttpStatus.FORBIDDEN),
    ACCOUNT_DELETED(1005, "Tài khoản này đã bị xóa.", HttpStatus.GONE),
    ACCOUNT_CAN_BE_RESTORED(1009, "Tài khoản này có thể được khôi phục. Vui lòng liên hệ hỗ trợ.", HttpStatus.CONFLICT),

    // Auth
    INCORRECT_PASSWORD(2001, "Tài khoản hoặc mật khẩu không chính xác.", HttpStatus.BAD_REQUEST),
    PASSWORD_ALREADY_SET(2002, "Tài khoản này đã được đặt mật khẩu từ trước.", HttpStatus.CONFLICT),
    INVALID_PASSWORD_FORMAT(2003, "Mật khẩu không hợp lệ. Vui lòng kiểm tra lại.", HttpStatus.BAD_REQUEST),
    NEW_PASSWORD_SAME_AS_OLD(2004, "Mật khẩu mới phải khác với mật khẩu cũ.", HttpStatus.BAD_REQUEST),
    TOKEN_INVALID(2005, "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(2006, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", HttpStatus.UNAUTHORIZED),

    // Registration / Duplicate
    PHONE_ALREADY_EXISTS(3001, "Số điện thoại này đã được đăng ký cho tài khoản khác.", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(3002, "Địa chỉ email này đã được đăng ký cho tài khoản khác.", HttpStatus.CONFLICT),
    GOOGLE_ACCOUNT_ALREADY_LINKED(3003, "Tài khoản Google này đã được liên kết với một tài khoản khác.", HttpStatus.CONFLICT),

    // OTP
    OTP_NOT_FOUND(4001, "Mã OTP không tồn tại hoặc đã hết hạn.", HttpStatus.NOT_FOUND),
    OTP_ALREADY_USED(4002, "Mã OTP này đã được sử dụng.", HttpStatus.CONFLICT),
    OTP_EXPIRED(4003, "Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới.", HttpStatus.GONE),
    OTP_MAX_ATTEMPTS(4004, "Bạn đã nhập sai OTP quá nhiều lần. Vui lòng yêu cầu mã mới.", HttpStatus.TOO_MANY_REQUESTS),
    OTP_RATE_LIMIT(4005, "Bạn đã yêu cầu gửi OTP quá nhiều lần. Vui lòng thử lại sau.", HttpStatus.TOO_MANY_REQUESTS),
    OTP_INVALID(4006, "Mã OTP không hợp lệ.", HttpStatus.BAD_REQUEST),

    // Validation
    INVALID_PHONE_FORMAT(5001, "Định dạng số điện thoại không hợp lệ.", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(5002, "Định dạng email không hợp lệ.", HttpStatus.BAD_REQUEST),
    PHONE_AND_EMAIL_REQUIRED(5003, "Vui lòng cung cấp cả số điện thoại và email.", HttpStatus.BAD_REQUEST),
    FULL_NAME_REQUIRED(5004, "Vui lòng nhập họ và tên.", HttpStatus.BAD_REQUEST),
    INVALID_FULL_NAME(5005, "Họ và tên không hợp lệ.", HttpStatus.BAD_REQUEST),

    // Google
    GOOGLE_AUTH_FAILED(6001, "Xác thực Google thất bại. Vui lòng thử lại.", HttpStatus.BAD_REQUEST),
    GOOGLE_TOKEN_INVALID(6002, "Mã xác thực Google không hợp lệ.", HttpStatus.BAD_REQUEST),

    // Session
    SESSION_NOT_FOUND(7001, "Không tìm thấy phiên đăng nhập. Vui lòng đăng nhập lại.", HttpStatus.NOT_FOUND),

    // QR Code
    QR_CODE_NOT_FOUND(8001, "Không tìm thấy mã QR.", HttpStatus.NOT_FOUND),
    QR_CODE_EXPIRED(8002, "Mã QR đã hết hạn. Vui lòng tạo mã mới.", HttpStatus.GONE),
    INVALID_QR_CODE(8003, "Mã QR không hợp lệ.", HttpStatus.BAD_REQUEST),
    QR_CODE_ALREADY_USED(8004, "Mã QR này đã được quét và sử dụng.", HttpStatus.CONFLICT),
    INVALID_QR_STATUS(8005, "Trạng thái mã QR không hợp lệ.", HttpStatus.BAD_REQUEST),
    INVALID_DEVICE_ID(8006, "Yêu cầu cung cấp ID thiết bị.", HttpStatus.BAD_REQUEST),
    TOO_MANY_PENDING_QR_LOGINS(8007, "Quá nhiều yêu cầu đăng nhập bằng QR đang chờ xử lý.", HttpStatus.TOO_MANY_REQUESTS),

    // Email
    EMAIL_SEND_FAILED(9001, "Lỗi hệ thống: Không thể gửi email. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR),

    // Internal
    INTERNAL_SERVICE_ERROR(9998, "Lỗi kết nối hệ thống nội bộ. Vui lòng thử lại sau.", HttpStatus.INTERNAL_SERVER_ERROR),

    // Backup Code (changed from 9999 to 9997 to avoid conflict with UNCATEGORIZED_EXCEPTION)
    INVALID_BACKUP_CODE(9997, "Mã backup code không hợp lệ.", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}