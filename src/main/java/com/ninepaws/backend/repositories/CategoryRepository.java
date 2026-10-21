package com.ninepaws.backend.repositories;

import com.ninepaws.backend.entities.Category;
import com.ninepaws.backend.enums.TransactionType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

  @Query(
      "SELECT c FROM Category c WHERE (c.user.id = :userId OR c.user IS NULL) AND c.type = :type")
  List<Category> findAllAvailableForUserAndType(
      @Param("userId") UUID userId, @Param("type") TransactionType type);

  List<Category> findByUserIdIsNull();
}
