package io.github.zzz1999.entityreskin.protocol;

/**
 * Thrown when a packet or manifest cannot be decoded/validated. Callers (client and server)
 * must catch this and degrade gracefully rather than propagating it into game logic.
 */
public class ProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
