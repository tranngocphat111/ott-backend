package mediaservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "relationship.socket.enabled=false")
class MediaServicesApplicationTests {

	@Test
	void contextLoads() {
	}

}
