package com.renterp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RentErpApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that all beans wire up correctly and Flyway migrations pass
    }
}
