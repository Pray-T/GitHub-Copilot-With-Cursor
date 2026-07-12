package com.demo.githubcopilotwithcursor.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            if (hasFailedMigration(flyway)) {
                log.warn("Detected failed Flyway migration history. Running repair() before migrate().");
                flyway.repair();
            }
            flyway.migrate();
        };
    }

    private boolean hasFailedMigration(Flyway flyway) {
        MigrationInfo[] migrations = flyway.info().all();
        if (migrations == null) {
            return false;
        }
        for (MigrationInfo migration : migrations) {
            if (migration != null && migration.getState() == MigrationState.FAILED) {
                return true;
            }
        }
        return false;
    }
}
