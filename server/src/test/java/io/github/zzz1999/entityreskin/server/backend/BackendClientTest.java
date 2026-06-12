package io.github.zzz1999.entityreskin.server.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BackendClientTest {

    private static final String VALID_HASH =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void parsesValidHashResponse() {
        assertEquals(VALID_HASH,
                BackendClient.parseSha256Response("{\"sha256\":\"" + VALID_HASH + "\"}"));
    }

    @Test
    void rejectsMalformedResponses() {
        assertNull(BackendClient.parseSha256Response("not json"));
        assertNull(BackendClient.parseSha256Response("[]"));
        assertNull(BackendClient.parseSha256Response("{}"));
        assertNull(BackendClient.parseSha256Response("{\"sha256\":12345}"));
        assertNull(BackendClient.parseSha256Response("{\"sha256\":\"XYZ\"}"));
        assertNull(BackendClient.parseSha256Response("{\"sha256\":\"" + VALID_HASH.substring(1) + "\"}"));
    }
}
