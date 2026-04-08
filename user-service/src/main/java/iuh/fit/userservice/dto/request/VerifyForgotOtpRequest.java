package iuh.fit.userservice.dto.request;

import lombok.Data;

@Data
public class VerifyForgotOtpRequest {
    private String phone;
    private String email;
    private String otp;
}
