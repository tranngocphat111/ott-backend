package iuh.fit.ottbackend.repository.httpclient;

import iuh.fit.ottbackend.dto.response.GoogleTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(
        name = "google-oauth-client",
        contextId = "googleIdentityClient",
        url = "https://oauth2.googleapis.com"
)
public interface GoogleIdentityClient {

    @PostMapping(
            value = "/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    GoogleTokenResponse exchangeToken(@RequestBody Map<String, ?> formParams);
}