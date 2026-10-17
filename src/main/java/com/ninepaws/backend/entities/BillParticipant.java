package com.ninepaws.backend.entities;

import com.ninepaws.backend.enums.ParticipantPaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bill_participants")
public class BillParticipant extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "bill_id", nullable = false)
  private SplitBill bill;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "transaction_id", unique = true)
  private Transaction transaction;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "avatar_url", columnDefinition = "TEXT")
  private String avatarUrl;

  @Column(name = "is_host", nullable = false)
  @Builder.Default
  private boolean isHost = false;

  @Column(name = "is_auto_balanced", nullable = false)
  @Builder.Default
  private boolean isAutoBalanced = false;

  @Column(name = "amount_owed", nullable = false, precision = 18, scale = 2)
  private BigDecimal amountOwed;

  @Column(name = "custom_note", length = 255)
  private String customNote;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_status", nullable = false, length = 20)
  @Builder.Default
  private ParticipantPaymentStatus paymentStatus = ParticipantPaymentStatus.UNPAID;

  @Column(name = "paid_at")
  private Instant paidAt;
}
