package com.renterp;

import io.github.cdimascio.dotenv.Dotenv;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RentErpApplication {

    private static final Logger log = LogManager.getLogger(RentErpApplication.class);

    public static void main(String[] args) {
        loadDotenv();
        SpringApplication.run(RentErpApplication.class, args);
        log.info("Rent ERP backend started — virtual threads enabled, timezone UTC");
    }

    /**
     * Loads .env into system properties before Spring's Environment initialises.
     *
     * Rules:
     *  - Real OS / Docker / CI environment variables always win (never overwritten)
     *  - .env is silently skipped if the file does not exist (production has no .env)
     *  - Missing required vars surface as startup failure in application.yml (no defaults)
     */
    private static void loadDotenv() {
        Dotenv dotenv = Dotenv.configure()
                .directory("./")
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry -> {
            // Only set if not already provided by the real environment
            if (System.getenv(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });

        log.debug("dotenv loaded — real env vars take precedence");
    }
}
