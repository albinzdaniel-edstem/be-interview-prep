package com.edstem.app.link.repository;

import com.edstem.app.link.entity.Link;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkRepository extends JpaRepository<Link, UUID> {

  Optional<Link> findByCode(String code);

  Optional<Link> findByUrlHash(String urlHash);

  @Modifying(clearAutomatically = true)
  @Query("update Link l set l.visitCount = l.visitCount + 1 where l.id = :id")
  int incrementVisitCount(@Param("id") UUID id);
}
