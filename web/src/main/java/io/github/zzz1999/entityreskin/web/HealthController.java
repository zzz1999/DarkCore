package io.github.zzz1999.entityreskin.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Unauthenticated liveness endpoint. */
@RestController
class HealthController {

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "ok", "service", "entityreskin-web");
    }
}
