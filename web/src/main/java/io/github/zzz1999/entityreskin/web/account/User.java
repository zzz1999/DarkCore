package io.github.zzz1999.entityreskin.web.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A registered account. {@code enabled} flips to true only after email verification. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Remaining downloadable-traffic credit, in bytes. */
    @Column(nullable = false)
    private long balanceBytes = 0;

    @Column(nullable = false)
    private long lifetimeRechargedBytes = 0;

    /** Balance below which a low-balance email alert fires; 0 disables the alert. */
    @Column(nullable = false)
    private long lowBalanceThresholdBytes = 0;

    /** Optional address for notifications; falls back to the account email when null. */
    @Column
    private String notificationEmail;

    @Column(unique = true)
    private String inviteCode;

    @Column
    private String invitedByEmail;

    /** When the one-shot low-balance alert was last sent; re-armed by a qualifying recharge. */
    @Column
    private Instant lowBalanceNotifiedAt;

    protected User() {
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getBalanceBytes() {
        return balanceBytes;
    }

    public void setBalanceBytes(long balanceBytes) {
        this.balanceBytes = balanceBytes;
    }

    public long getLifetimeRechargedBytes() {
        return lifetimeRechargedBytes;
    }

    public void setLifetimeRechargedBytes(long lifetimeRechargedBytes) {
        this.lifetimeRechargedBytes = lifetimeRechargedBytes;
    }

    public long getLowBalanceThresholdBytes() {
        return lowBalanceThresholdBytes;
    }

    public void setLowBalanceThresholdBytes(long lowBalanceThresholdBytes) {
        this.lowBalanceThresholdBytes = lowBalanceThresholdBytes;
    }

    public String getNotificationEmail() {
        return notificationEmail;
    }

    public void setNotificationEmail(String notificationEmail) {
        this.notificationEmail = notificationEmail;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public String getInvitedByEmail() {
        return invitedByEmail;
    }

    public void setInvitedByEmail(String invitedByEmail) {
        this.invitedByEmail = invitedByEmail;
    }

    public Instant getLowBalanceNotifiedAt() {
        return lowBalanceNotifiedAt;
    }

    public void setLowBalanceNotifiedAt(Instant lowBalanceNotifiedAt) {
        this.lowBalanceNotifiedAt = lowBalanceNotifiedAt;
    }
}
