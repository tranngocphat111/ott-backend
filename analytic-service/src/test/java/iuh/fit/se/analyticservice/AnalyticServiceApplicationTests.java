package iuh.fit.se.analyticservice;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-test-secret",
        "spring.datasource.url=jdbc:h2:mem:analytics_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "analytics.cache.enabled=false",
        "analytics.daily-stats.enabled=false",
        "analytics.archive.enabled=false",
        "analytics.dlq-monitor.enabled=false"
})
class AnalyticServiceApplicationTests {

    @MockBean
    private RabbitAdmin rabbitAdmin;

    @Test
    void contextLoads() {
    }

}
