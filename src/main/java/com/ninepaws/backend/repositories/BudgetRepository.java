package com.ninepaws.backend.repositories;

import com.ninepaws.backend.entities.Budget;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

  List<Budget> findByUserIdAndMonthAndYear(UUID userId, int month, int year);

  Optional<Budget> findByUserIdAndCategoryIdAndMonthAndYear(
      UUID userId, UUID categoryId, int month, int year);

  Optional<Budget> findByUserIdAndCategoryIsNullAndMonthAndYear(UUID userId, int month, int year);
}
