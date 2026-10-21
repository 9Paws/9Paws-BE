package com.ninepaws.backend.repositories;

import com.ninepaws.backend.entities.Notification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

  long countByUserIdAndIsReadFalse(UUID userId);
}
