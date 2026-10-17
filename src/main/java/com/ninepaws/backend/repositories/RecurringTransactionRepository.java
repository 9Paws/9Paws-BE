package com.ninepaws.backend.repositories;

import com.ninepaws.backend.entities.RecurringTransaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {

  @Query("SELECT r FROM RecurringTransaction r WHERE r.wallet.user.id = :userId")
  List<RecurringTransaction> findAllByUserId(@Param("userId") UUID userId);

  @Query("SELECT r FROM RecurringTransaction r WHERE r.id = :id AND r.wallet.user.id = :userId")
  Optional<RecurringTransaction> findByIdAndUserId(
      @Param("id") UUID id, @Param("userId") UUID userId);

  List<RecurringTransaction> findByIsActiveTrueAndNextOccurrenceDateLessThanEqual(LocalDate date);
}
