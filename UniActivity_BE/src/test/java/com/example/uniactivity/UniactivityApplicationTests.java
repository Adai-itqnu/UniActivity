package com.example.uniactivity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"app.jwt.secret=test-only-jwt-secret-with-at-least-32-bytes",
		"app.qr.secret=test-only-qr-secret-that-is-distinct-32-bytes"
})
class UniactivityApplicationTests {

	@Test
	void contextLoads() {
	}

}
