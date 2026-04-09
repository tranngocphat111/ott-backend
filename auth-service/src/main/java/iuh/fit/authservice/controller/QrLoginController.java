package iuh.fit.authservice.controller;

import iuh.fit.authservice.dto.request.QrConfirmRequest;
import iuh.fit.authservice.dto.request.QrGenerateRequest;
import iuh.fit.authservice.dto.request.QrScanRequest;
import iuh.fit.authservice.dto.response.ApiResponse;
import iuh.fit.authservice.dto.response.QrCodeResponse;
import iuh.fit.authservice.dto.response.QrStatusResponse;
import iuh.fit.authservice.service.QrLoginService;
import iuh.fit.authservice.utils.ControllerUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/qr")
@RequiredArgsConstructor
@Slf4j
public class QrLoginController {

    private final QrLoginService qrLoginService;
    private final ControllerUtils controllerUtils;

    @PostMapping("/generate")
    public ApiResponse<QrCodeResponse> generateQrCode(
            @Valid @RequestBody QrGenerateRequest request,
            HttpServletRequest httpRequest) {

        controllerUtils.enrichWithClientInfo(request, httpRequest);
        QrCodeResponse response = qrLoginService.generateLoginQrCode(request);

        return ApiResponse.<QrCodeResponse>builder()
                .result(response)
                .message("QR code generated successfully. Please scan with your mobile app.")
                .build();
    }

    @PostMapping("/scan")
    public ApiResponse<QrStatusResponse> scanQrCode(
            @Valid @RequestBody QrScanRequest request,
            HttpServletRequest httpRequest) {

        String userId = controllerUtils.getCurrentUserId();
        controllerUtils.enrichWithClientInfo(request, httpRequest);

        QrStatusResponse response = qrLoginService.scanQrCode(request, userId);

        return ApiResponse.<QrStatusResponse>builder()
                .result(response)
                .build();
    }

    @PostMapping("/confirm")
    public ApiResponse<QrStatusResponse> confirmQrLogin(
            @Valid @RequestBody QrConfirmRequest request) {

        String userId = controllerUtils.getCurrentUserId();
        QrStatusResponse response = qrLoginService.confirmQrLogin(request, userId);

        return ApiResponse.<QrStatusResponse>builder()
                .result(response)
                .build();
    }

    @GetMapping("/status/{qrId}")
    public ApiResponse<QrStatusResponse> checkQrStatus(@PathVariable String qrId) {
        QrStatusResponse response = qrLoginService.checkQrStatus(qrId);

        return ApiResponse.<QrStatusResponse>builder()
                .result(response)
                .build();
    }

    @DeleteMapping("/{qrId}")
    public ApiResponse<Void> cancelQrCode(@PathVariable String qrId) {
        qrLoginService.cancelQrCode(qrId);

        return ApiResponse.<Void>builder()
                .message("QR code cancelled successfully")
                .build();
    }
}