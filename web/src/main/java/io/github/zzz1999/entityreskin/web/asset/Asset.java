package io.github.zzz1999.entityreskin.web.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Metadata for an uploaded asset. The primary key IS the content hash (content-addressed,
 * globally deduplicated). The backend stores bytes opaquely and records only metadata here.
 */
@Entity
@Table(name = "assets")
public class Asset {

    @Id
    private String sha256;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private String kind;

    @Column
    private String contentType;

    @Column(nullable = false)
    private String ownerEmail;

    @Column
    private String originalFilename;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Asset() {
    }

    public Asset(String sha256, long size, String kind, String contentType,
                 String ownerEmail, String originalFilename) {
        this.sha256 = sha256;
        this.size = size;
        this.kind = kind;
        this.contentType = contentType;
        this.ownerEmail = ownerEmail;
        this.originalFilename = originalFilename;
    }

    public String getSha256() {
        return sha256;
    }

    public long getSize() {
        return size;
    }

    public String getKind() {
        return kind;
    }

    public String getContentType() {
        return contentType;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
