package com.ninepaws.backend.repositories;

import com.ninepaws.backend.entities.Provider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, UUID> {
  Optional<Provider> findByCode(String code);
}
