package iuh.fit.ottbackend.mapper;

import iuh.fit.ottbackend.dto.response.QrCodeResponse;
import iuh.fit.ottbackend.dto.response.QrStatusResponse;
import iuh.fit.ottbackend.entity.QrCode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface QrCodeMapper {

    @Mapping(source = "id", target = "qrId")
    @Mapping(source = "status", target = "status")
    QrCodeResponse toQrCodeResponse(QrCode qrCode);


    @Mapping(source = "id", target = "qrId")
    @Mapping(source = "status", target = "status")
    @Mapping(target = "sessionToken", ignore = true)
    @Mapping(target = "refreshToken", ignore = true)
    @Mapping(target = "message", ignore = true)
    QrStatusResponse toQrStatusResponse(QrCode qrCode);
}