package io.github.zzz1999.entityreskin.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: verifies the full Spring application context wires up (data source, JPA,
 * security filter chain, JWT service, mail, controllers). Overrides are inlined so the test
 * uses an in-memory database and a temporary blob root.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:entityreskin-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "entityreskin.storage.root=build/test-blobs",
        "entityreskin.jwt.secret=test-secret-0123456789abcdef0123456789abcdef0123456789",
        "entityreskin.email.dev-mode=true"
})
class EntityReskinWebApplicationTests {

    @Test
    void contextLoads() {
    }
}
