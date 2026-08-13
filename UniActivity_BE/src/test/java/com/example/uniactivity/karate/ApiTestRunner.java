package com.example.uniactivity.karate;

import com.example.uniactivity.entity.User;
import com.example.uniactivity.enums.Role;
import com.example.uniactivity.enums.UserStatus;
import com.example.uniactivity.repository.UserRepository;
import com.intuit.karate.junit5.Karate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiTestRunner {

    static final String USERNAME = "karate-student";
    static final String PASSWORD = "Karate123@";

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeAll
    void configureServerAndFixture() {
        System.setProperty("karate.server.port", Integer.toString(port));
        if (userRepository.findByUsername(USERNAME).isPresent()) {
            return;
        }

        User user = new User();
        user.setUsername(USERNAME);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Karate Student");
        user.setEmail("karate-student@example.test");
        user.setRole(Role.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        user.setProvider("LOCAL");
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
    }

    @Karate.Test
    Karate testApi() {
        return Karate.run("api-test").relativeTo(getClass());
    }
}
