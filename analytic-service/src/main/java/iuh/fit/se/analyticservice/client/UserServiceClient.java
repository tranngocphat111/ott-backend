package iuh.fit.se.analyticservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import iuh.fit.se.analyticservice.dto.ApiResponseDTO;
import iuh.fit.se.analyticservice.dto.UserDetailDTO;

@FeignClient(name = "user-service", url = "${user-service.url}")
public interface UserServiceClient {

    @GetMapping("/internal/users/{userId}")
    ApiResponseDTO<UserDetailDTO> getUserById(
            @PathVariable("userId") String userId,
            @RequestHeader("X-Internal-Key") String internalKey
    );
}
