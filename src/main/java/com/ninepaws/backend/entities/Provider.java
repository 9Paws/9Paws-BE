package com.ninepaws.backend.entities;

import com.ninepaws.backend.enums.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
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
@Table(name = "providers")
public class Provider extends BaseEntity {

  @Column(name = "code", nullable = false, unique = true, length = 50)
  private String code;

  @Column(name = "bin_code", length = 20)
  private String binCode;

  @Column(name = "short_name", nullable = false, length = 100)
  private String shortName;

  @Column(name = "full_name", nullable = false, length = 255)
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  private ProviderType type;

  @Column(name = "logo_url", nullable = false, columnDefinition = "TEXT")
  private String logoUrl;
}
