package com.ninepaws.backend.repositories;

import com.ninepaws.backend.entities.ChatMessage;
import com.ninepaws.backend.enums.ChatMode;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

  Page<ChatMessage> findByUserIdAndChatModeOrderByCreatedAtDesc(
      UUID userId, ChatMode chatMode, Pageable pageable);
}
