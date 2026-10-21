package com.ninepaws.backend.entities;

import com.ninepaws.backend.enums.NotificationActionStatus;
import com.ninepaws.backend.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
public class Notification extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recurring_trans_id")
  private RecurringTransaction recurringTransaction;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "budget_id")
  private Budget budget;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_transaction_id", unique = true)
  private Transaction createdTransaction;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 30)
  private NotificationType type;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_status", length = 20)
  private NotificationActionStatus actionStatus;

  @Column(name = "is_read", nullable = false)
  @Builder.Default
  private boolean isRead = false;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
