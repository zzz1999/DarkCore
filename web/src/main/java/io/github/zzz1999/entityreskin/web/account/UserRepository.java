package io.github.zzz1999.entityreskin.web.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmailAndEnabledTrue(String email);

    Optional<User> findByInviteCode(String inviteCode);

    long countByInvitedByEmail(String invitedByEmail);

    /**
     * Atomic conditional debit; returns rows updated (1 = charged, 0 = insufficient). Atomicity
     * prevents concurrent downloads from jointly overdrawing. {@code clearAutomatically} so a
     * subsequent read in the same transaction sees the new balance.
     */
    @Modifying(clearAutomatically = true)
    @Query("update User u set u.balanceBytes = u.balanceBytes - :bytes "
            + "where u.email = :email and u.balanceBytes >= :bytes")
    int debit(@Param("email") String email, @Param("bytes") long bytes);

    /** Atomic credit (balance and lifetime total); avoids read-modify-write lost updates. */
    @Modifying(clearAutomatically = true)
    @Query("update User u set u.balanceBytes = u.balanceBytes + :bytes, "
            + "u.lifetimeRechargedBytes = u.lifetimeRechargedBytes + :bytes where u.email = :email")
    int credit(@Param("email") String email, @Param("bytes") long bytes);

    /** Re-arms the one-shot low-balance alert once the balance is at or above the threshold. */
    @Modifying(clearAutomatically = true)
    @Query("update User u set u.lowBalanceNotifiedAt = null where u.email = :email "
            + "and (u.lowBalanceThresholdBytes <= 0 or u.balanceBytes >= u.lowBalanceThresholdBytes)")
    int rearmLowBalanceAlert(@Param("email") String email);

    /** Atomically claims the one-shot low-balance alert; returns 1 to the caller that should send it. */
    @Modifying(clearAutomatically = true)
    @Query("update User u set u.lowBalanceNotifiedAt = :now where u.email = :email "
            + "and u.lowBalanceNotifiedAt is null and u.lowBalanceThresholdBytes > 0 "
            + "and u.balanceBytes < u.lowBalanceThresholdBytes")
    int markLowBalanceNotified(@Param("email") String email, @Param("now") Instant now);
}
