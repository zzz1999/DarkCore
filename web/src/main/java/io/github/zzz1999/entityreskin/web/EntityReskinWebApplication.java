package io.github.zzz1999.entityreskin.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EntityReskin resource backend: accounts, asset uploads (content-addressed), per-server manifest
 * generation, rate-limited signed downloads, billing, and live statistics. Reuses the
 * {@code shared} module's manifest types so the format never drifts from what the client
 * validates. Scheduling is enabled for the in-memory statistics sweep.
 */
@SpringBootApplication
@EnableScheduling
public class EntityReskinWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(EntityReskinWebApplication.class, args);
    }
}
