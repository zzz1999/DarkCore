package io.github.zzz1999.entityreskin.web.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, String> {

    @Query("select coalesce(sum(a.size), 0) from Asset a where a.ownerEmail = :ownerEmail")
    long totalSizeByOwner(@Param("ownerEmail") String ownerEmail);
}
