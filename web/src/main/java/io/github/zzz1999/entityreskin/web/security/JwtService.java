package io.github.zzz1999.entityreskin.web.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Issues and verifies HMAC-signed JWTs. Subject = account email. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlMinutes;

    public JwtService(@Value("${entityreskin.jwt.secret}") String secret,
                      @Value("${entityreskin.jwt.ttl-minutes}") long ttlMinutes,
                      Environment environment) {
        Secrets.requireStrong(secret, "entityreskin.jwt.secret", environment);
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = ttlMinutes;
    }

    public String issue(String subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public String parseSubject(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public long ttlMinutes() {
        return ttlMinutes;
    }
}
