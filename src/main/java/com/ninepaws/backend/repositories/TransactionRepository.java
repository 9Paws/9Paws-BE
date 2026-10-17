package com.ninepaws.backend.repositories;

import com.ninepaws.backend.entities.Transaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

  @Query(
      """
        SELECT t FROM Transaction t
        JOIN FETCH t.wallet w
        JOIN FETCH t.category c
        WHERE t.id = :id AND w.user.id = :userId
        """)
  Optional<Transaction> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

  @Query(
      value =
          """
        SELECT t FROM Transaction t
        JOIN t.wallet w
        WHERE w.user.id = :userId
        ORDER BY t.transactionDate DESC
        """,
      countQuery =
          """
        SELECT COUNT(t) FROM Transaction t
        JOIN t.wallet w
        WHERE w.user.id = :userId
        """)
  Page<Transaction> findAllByUserId(@Param("userId") UUID userId, Pageable pageable);
}
