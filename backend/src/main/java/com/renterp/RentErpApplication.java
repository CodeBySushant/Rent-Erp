package com.renterp;

import io.github.cdimascio.dotenv.Dotenv;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // M16: HousekeepingJob
public class RentErpApplication {

    public static void main(String[] args) {
        // .env first, logging second: the log levels in log4j2.xml read
        // APP_LOG_LEVEL etc. from system properties, which loadDotenv sets.
        // A static logger field would start Log4j2 before .env is read (C10).
        loadDotenv();
        SpringApplication.run(RentErpApplication.class, args);
        Logger log = LogManager.getLogger(RentErpApplication.class);
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
    // Package-private so the Spring context test can load backend/.env too.
    static void loadDotenv() {
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

    }
}
