package com.ninepaws.backend.entities;

import com.ninepaws.backend.enums.RecurringFrequency;
import com.ninepaws.backend.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "recurring_transactions")
public class RecurringTransaction extends BaseAuditEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "wallet_id", nullable = false)
  private Wallet wallet;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private Category category;

  @Column(name = "title", nullable = false, length = 150)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  @Builder.Default
  private TransactionType type = TransactionType.EXPENSE;

  @Column(name = "estimated_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal estimatedAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "frequency", nullable = false, length = 20)
  private RecurringFrequency frequency;

  @Column(name = "day_of_period")
  private Integer dayOfPeriod;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "next_occurrence_date", nullable = false)
  private LocalDate nextOccurrenceDate;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private boolean isActive = true;
}
