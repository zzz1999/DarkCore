package io.github.zzz1999.entityreskin.web.security;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Verifies a Cloudflare Turnstile captcha token on abuse-prone unauthenticated endpoints
 * (registration, password reset). When no secret is configured the verifier is a no-op, so the
 * backend runs without a captcha provider during development; production sets the secret and the
 * frontend supplies the widget token. Rate limiting remains the primary defense regardless.
 */
@Component
public class CaptchaVerifier {

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String secret;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CaptchaVerifier(@Value("${entityreskin.captcha.secret:}") String secret) {
        this.secret = secret;
    }

    public boolean isEnabled() {
        return secret != null && !secret.isBlank();
    }

    public void verify(String token, String remoteIp) {
        if (!isEnabled()) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "captcha token required");
        }
        boolean success;
        try {
            StringBuilder form = new StringBuilder();
            form.append("secret=").append(encode(secret));
            form.append("&response=").append(encode(token));
            if (remoteIp != null && !remoteIp.isBlank()) {
                form.append("&remoteip=").append(encode(remoteIp));
            }
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(VERIFY_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            success = body.has("success") && body.get("success").getAsBoolean();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "captcha verification unavailable");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "captcha verification unavailable");
        }
        if (!success) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "captcha verification failed");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
