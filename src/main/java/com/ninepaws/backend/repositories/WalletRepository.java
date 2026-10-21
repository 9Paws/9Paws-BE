package com.ninepaws.backend.repositories;

import com.ninepaws.backend.entities.Wallet;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

  List<Wallet> findByUserIdAndIsArchivedFalse(UUID userId);

  Optional<Wallet> findByIdAndUserIdAndIsArchivedFalse(UUID id, UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT w FROM Wallet w WHERE w.id = :id AND w.user.id = :userId AND w.isArchived = false")
  Optional<Wallet> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);
}
