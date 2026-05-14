package iuh.fit.se.analyticservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import iuh.fit.se.analyticservice.dto.UserDetailDTO;

@FeignClient(name = "user-service", url = "${user-service.url}")
public interface UserServiceClient {

    @GetMapping("/api/v1/users/{userId}")
    UserDetailDTO getUserById(@PathVariable("userId") String userId);
}
