package iuh.fit.authservice.repository.httpclient;

import iuh.fit.authservice.dto.response.GoogleUserInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "google-user-client",
        contextId = "googleUserClient",
        url = "https://www.googleapis.com"
)
public interface GoogleUserClient {

    @GetMapping(value = "/oauth2/v1/userinfo")
    GoogleUserInfo getUserInfo(
            @RequestParam("alt") String alt,
            @RequestParam("access_token") String accessToken
    );
}