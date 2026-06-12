package io.github.zzz1999.entityreskin.web.appearance;

import io.github.zzz1999.entityreskin.web.server.GameServer;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Map;

/**
 * One appearance definition for a server: an identifier (for example {@code entityreskin:dragon_red})
 * mapped to the content-addressed assets that render it. {@code resources} maps a resource kind
 * to the SHA-256 of an uploaded asset; referenced assets are validated to exist with a matching
 * kind before the entry is saved.
 */
@Entity
@Table(name = "appearance_entries",
        uniqueConstraints = @UniqueConstraint(name = "uk_appearance_server_identifier",
                columnNames = {"game_server_id", "identifier"}))
public class AppearanceEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_server_id", nullable = false)
    private GameServer gameServer;

    @Column(nullable = false, length = 128)
    private String identifier;

    @Column(length = 64)
    private String displayName;

    @Column(nullable = false, length = 128)
    private String geometryName;

    @Column(nullable = false, length = 128)
    private String defaultAnimation;

    @Column(length = 128)
    private String renderControllerEntry;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "appearance_resources", joinColumns = @JoinColumn(name = "appearance_id"))
    @MapKeyColumn(name = "kind", length = 32)
    @Column(name = "sha256", nullable = false, length = 64)
    private Map<String, String> resources;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected AppearanceEntry() {
    }

    public AppearanceEntry(GameServer gameServer, String identifier, String displayName,
                           String geometryName, String defaultAnimation, String renderControllerEntry,
                           Map<String, String> resources) {
        this.gameServer = gameServer;
        this.identifier = identifier;
        this.displayName = displayName;
        this.geometryName = geometryName;
        this.defaultAnimation = defaultAnimation;
        this.renderControllerEntry = renderControllerEntry;
        this.resources = resources;
    }

    public Long getId() {
        return id;
    }

    public GameServer getGameServer() {
        return gameServer;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getGeometryName() {
        return geometryName;
    }

    public void setGeometryName(String geometryName) {
        this.geometryName = geometryName;
    }

    public String getDefaultAnimation() {
        return defaultAnimation;
    }

    public void setDefaultAnimation(String defaultAnimation) {
        this.defaultAnimation = defaultAnimation;
    }

    public String getRenderControllerEntry() {
        return renderControllerEntry;
    }

    public void setRenderControllerEntry(String renderControllerEntry) {
        this.renderControllerEntry = renderControllerEntry;
    }

    public Map<String, String> getResources() {
        return resources;
    }

    public void setResources(Map<String, String> resources) {
        this.resources = resources;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
