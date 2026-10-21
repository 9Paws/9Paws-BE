package com.ninepaws.backend.entities;

import com.ninepaws.backend.enums.SplitBillStatus;
import com.ninepaws.backend.enums.SplitMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "split_bills")
public class SplitBill extends BaseAuditEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "receiving_wallet_id", nullable = false)
  private Wallet receivingWallet;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "tag", length = 50)
  private String tag;

  @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
  private BigDecimal totalAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "split_method", nullable = false, length = 20)
  private SplitMethod splitMethod;

  @Column(name = "event_date", nullable = false)
  private Instant eventDate;

  @Column(name = "location_name", length = 200)
  private String locationName;

  @Column(name = "location_address", columnDefinition = "TEXT")
  private String locationAddress;

  @Column(name = "latitude")
  private Double latitude;

  @Column(name = "longitude")
  private Double longitude;

  @Column(name = "vietqr_bank_bin", nullable = false, length = 20)
  private String vietqrBankBin;

  @Column(name = "vietqr_account_no", nullable = false, length = 50)
  private String vietqrAccountNo;

  @Column(name = "vietqr_account_name", nullable = false, length = 100)
  private String vietqrAccountName;

  @Column(name = "vietqr_transfer_message", length = 100)
  private String vietqrTransferMessage;

  @Column(name = "share_token", nullable = false, unique = true, length = 100)
  private String shareToken;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private SplitBillStatus status = SplitBillStatus.COLLECTING;
}
