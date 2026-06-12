package io.github.zzz1999.entityreskin.web.account;

import io.github.zzz1999.entityreskin.web.security.AttemptLimiter;
import io.github.zzz1999.entityreskin.web.security.CaptchaVerifier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int REQUESTS_PER_ADDRESS_WINDOW = 20;
    private static final Duration ADDRESS_WINDOW = Duration.ofHours(1);

    private final AuthService authService;
    private final AttemptLimiter attemptLimiter;
    private final CaptchaVerifier captchaVerifier;

    public AuthController(AuthService authService, AttemptLimiter attemptLimiter,
                          CaptchaVerifier captchaVerifier) {
        this.authService = authService;
        this.attemptLimiter = attemptLimiter;
        this.captchaVerifier = captchaVerifier;
    }

    @PostMapping("/register")
    public Map<String, String> register(@Valid @RequestBody RegisterRequest request,
                                        HttpServletRequest httpRequest) {
        rateLimitByAddress(httpRequest);
        captchaVerifier.verify(request.captchaToken(), httpRequest.getRemoteAddr());
        authService.register(request.email(), request.password(), request.inviteCode());
        return Map.of("status", "verification_sent");
    }

    @PostMapping("/verify")
    public Map<String, String> verify(@Valid @RequestBody VerifyRequest request,
                                      HttpServletRequest httpRequest) {
        rateLimitByAddress(httpRequest);
        authService.verify(request.email(), request.code());
        return Map.of("status", "verified");
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.email(), request.password());
        return new TokenResponse(token, authService.tokenTtlMinutes());
    }

    @PostMapping("/forgot")
    public Map<String, String> forgot(@Valid @RequestBody ForgotRequest request,
                                      HttpServletRequest httpRequest) {
        rateLimitByAddress(httpRequest);
        captchaVerifier.verify(request.captchaToken(), httpRequest.getRemoteAddr());
        authService.forgotPassword(request.email());
        // Generic response regardless of whether the account exists (no enumeration).
        return Map.of("status", "reset_sent");
    }

    @PostMapping("/reset")
    public Map<String, String> reset(@Valid @RequestBody ResetRequest request,
                                     HttpServletRequest httpRequest) {
        rateLimitByAddress(httpRequest);
        authService.resetPassword(request.email(), request.code(), request.newPassword());
        return Map.of("status", "password_reset");
    }

    private void rateLimitByAddress(HttpServletRequest httpRequest) {
        if (!attemptLimiter.tryAcquire("auth-ip:" + httpRequest.getRemoteAddr(),
                REQUESTS_PER_ADDRESS_WINDOW, ADDRESS_WINDOW)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "too many requests");
        }
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String password,
            @Size(max = 16) String inviteCode,
            String captchaToken) {
    }

    public record VerifyRequest(@Email @NotBlank String email,
                                @NotBlank @Size(min = 4, max = 10) String code) {
    }

    public record LoginRequest(@Email @NotBlank String email,
                               @NotBlank String password) {
    }

    public record TokenResponse(String token, long expiresInMinutes) {
    }

    public record ForgotRequest(@Email @NotBlank String email, String captchaToken) {
    }

    public record ResetRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 4, max = 10) String code,
            @NotBlank @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String newPassword) {
    }
}
