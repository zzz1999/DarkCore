package io.github.zzz1999.entityreskin.web.server;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameServerRepository extends JpaRepository<GameServer, Long> {

    Optional<GameServer> findByToken(String token);

    List<GameServer> findByOwnerEmailOrderByCreatedAtAsc(String ownerEmail);
}
