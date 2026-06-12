package io.github.zzz1999.entityreskin.web.appearance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppearanceEntryRepository extends JpaRepository<AppearanceEntry, Long> {

    List<AppearanceEntry> findByGameServerId(Long gameServerId);

    Optional<AppearanceEntry> findByGameServerIdAndIdentifier(Long gameServerId, String identifier);

    void deleteByGameServerId(Long gameServerId);
}
