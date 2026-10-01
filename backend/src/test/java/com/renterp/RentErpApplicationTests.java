package com.renterp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RentErpApplicationTests {

    // main() loads backend/.env before Spring starts; a test does not go through
    // main(), so without this every ${VAR} in application.yml is unresolved.
    // Needs the local database from .env to be running, like the app itself.
    static {
        RentErpApplication.loadDotenv();
    }

    @Test
    void contextLoads() {
        // Verifies that all beans wire up correctly and Flyway migrations pass
    }
}
