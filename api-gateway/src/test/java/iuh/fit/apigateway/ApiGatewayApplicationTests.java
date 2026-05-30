package iuh.fit.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-test-secret"
})
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }

}
