package com.ninepaws.backend.repositories;

import com.ninepaws.backend.entities.SplitBill;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SplitBillRepository extends JpaRepository<SplitBill, UUID> {

  Optional<SplitBill> findByShareToken(String shareToken);

  @Query("SELECT sb FROM SplitBill sb WHERE sb.id = :id AND sb.receivingWallet.user.id = :userId")
  Optional<SplitBill> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

  @Query(
      value =
          "SELECT sb FROM SplitBill sb WHERE sb.receivingWallet.user.id = :userId ORDER BY sb.eventDate DESC",
      countQuery = "SELECT COUNT(sb) FROM SplitBill sb WHERE sb.receivingWallet.user.id = :userId")
  Page<SplitBill> findAllByUserId(@Param("userId") UUID userId, Pageable pageable);
}
