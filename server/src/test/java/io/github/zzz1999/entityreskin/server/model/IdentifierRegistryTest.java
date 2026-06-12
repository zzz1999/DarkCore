package io.github.zzz1999.entityreskin.server.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IdentifierRegistryTest {

    private static final Logger LOGGER = Logger.getLogger("IdentifierRegistryTest");

    @Test
    void setClearAndLookup() {
        IdentifierRegistry registry = new IdentifierRegistry(LOGGER);
        UUID uuid = UUID.randomUUID();
        registry.set(uuid, "entityreskin:dragon_red");
        assertEquals("entityreskin:dragon_red", registry.get(uuid));
        assertEquals("entityreskin:dragon_red", registry.clear(uuid));
        assertNull(registry.get(uuid));
    }

    @Test
    void persistenceRoundTrip(@TempDir File directory) {
        File file = new File(directory, "identifiers.yml");
        IdentifierRegistry original = new IdentifierRegistry(LOGGER);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        original.set(first, "entityreskin:dragon_red");
        original.set(second, "entityreskin:dragon_blue");
        original.save(file);

        IdentifierRegistry loaded = new IdentifierRegistry(LOGGER);
        loaded.load(file);
        assertEquals(2, loaded.size());
        assertEquals("entityreskin:dragon_red", loaded.get(first));
        assertEquals("entityreskin:dragon_blue", loaded.get(second));
    }

    @Test
    void loadSkipsInvalidEntries(@TempDir File directory) throws Exception {
        File file = new File(directory, "identifiers.yml");
        String yaml = "not-a-uuid: entityreskin:dragon_red\n"
                + UUID.randomUUID() + ": 'INVALID IDENTIFIER'\n";
        Files.write(file.toPath(), yaml.getBytes(StandardCharsets.UTF_8));

        IdentifierRegistry registry = new IdentifierRegistry(LOGGER);
        registry.load(file);
        assertEquals(0, registry.size());
    }
}
