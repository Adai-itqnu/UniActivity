package com.example.uniactivity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"app.jwt.secret=test-only-jwt-secret-with-at-least-32-bytes",
		"app.qr.secret=test-only-qr-secret-that-is-distinct-32-bytes",
		"spring.datasource.url=jdbc:h2:mem:uniactivity;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.security.oauth2.client.registration.google.client-id=test-google-client",
		"spring.security.oauth2.client.registration.google.client-secret=test-google-secret"
})
class UniactivityApplicationTests {

	@Test
	void contextLoads() {
	}

}
