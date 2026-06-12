package io.github.zzz1999.entityreskin.web.server;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A Minecraft server registered by an account. The {@code token} authorizes manifest fetches and
 * download-URL signatures for this server and selects its rate-limit bucket; it is distributed
 * to players via the manifest URL by design, so the damage from a leaked token is bounded by
 * this server's own rate tier. Resetting the token immediately invalidates outstanding signed
 * URLs.
 */
@Entity
@Table(name = "game_servers", indexes = @Index(name = "idx_game_servers_token", columnList = "token", unique = true))
public class GameServer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false)
    private String ownerEmail;

    /** Aggregate download rate for this server's token bucket; raised by paid tiers. */
    @Column(nullable = false)
    private long bytesPerSecond;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected GameServer() {
    }

    public GameServer(String name, String token, String ownerEmail, long bytesPerSecond) {
        this.name = name;
        this.token = token;
        this.ownerEmail = ownerEmail;
        this.bytesPerSecond = bytesPerSecond;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public long getBytesPerSecond() {
        return bytesPerSecond;
    }

    public void setBytesPerSecond(long bytesPerSecond) {
        this.bytesPerSecond = bytesPerSecond;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
