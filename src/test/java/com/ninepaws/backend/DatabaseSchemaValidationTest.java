package com.ninepaws.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.ninepaws.backend.entities.Provider;
import com.ninepaws.backend.entities.User;
import com.ninepaws.backend.entities.Wallet;
import com.ninepaws.backend.enums.AuthProvider;
import com.ninepaws.backend.enums.TransactionType;
import com.ninepaws.backend.enums.WalletType;
import com.ninepaws.backend.repositories.CategoryRepository;
import com.ninepaws.backend.repositories.ProviderRepository;
import com.ninepaws.backend.repositories.UserRepository;
import com.ninepaws.backend.repositories.WalletRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DatabaseSchemaValidationTest {

  @Autowired private ProviderRepository providerRepository;

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private WalletRepository walletRepository;

  @Test
  @DisplayName("V2 Seed: Providers are seeded correctly with bank and e-wallet master data")
  void shouldHaveSeedProviders() {
    List<Provider> providers = providerRepository.findAll();
    assertThat(providers).isNotEmpty();
    assertThat(providers).hasSizeGreaterThanOrEqualTo(11);

    assertThat(providerRepository.findByCode("VCB")).isPresent();
    assertThat(providerRepository.findByCode("MOMO")).isPresent();
  }

  @Test
  @DisplayName("V2 Seed: System default categories are seeded (user_id IS NULL)")
  void shouldHaveSystemDefaultCategories() {
    var systemCategories = categoryRepository.findByUserIdIsNull();
    assertThat(systemCategories).isNotEmpty();
    assertThat(systemCategories).hasSizeGreaterThanOrEqualTo(13);

    var expenseCategories =
        categoryRepository.findAllAvailableForUserAndType(null, TransactionType.EXPENSE);
    assertThat(expenseCategories).anyMatch(c -> c.getName().equals("Ẩm thực"));

    var incomeCategories =
        categoryRepository.findAllAvailableForUserAndType(null, TransactionType.INCOME);
    assertThat(incomeCategories).anyMatch(c -> c.getName().equals("Tiền lương"));
  }

  @Test
  @DisplayName(
      "JPA Auditing & Transitive Entities: Can persist User and Wallet with audit timestamps")
  void shouldPersistUserAndWalletWithAuditTimestamps() {
    User user =
        User.builder()
            .fullName("Nguyễn Văn Test")
            .email("test.auditing@ninepaws.com")
            .authProvider(AuthProvider.LOCAL)
            .build();

    User savedUser = userRepository.saveAndFlush(user);
    assertThat(savedUser.getId()).isNotNull();
    assertThat(savedUser.getCreatedAt()).isNotNull();
    assertThat(savedUser.getUpdatedAt()).isNotNull();

    Wallet wallet =
        Wallet.builder()
            .user(savedUser)
            .name("Ví tiền mặt test")
            .type(WalletType.CASH)
            .balance(new BigDecimal("500000.00"))
            .build();

    Wallet savedWallet = walletRepository.saveAndFlush(wallet);
    assertThat(savedWallet.getId()).isNotNull();
    assertThat(savedWallet.getUser().getId()).isEqualTo(savedUser.getId());
    assertThat(savedWallet.getBalance()).isEqualByComparingTo("500000.00");
    assertThat(savedWallet.getCreatedAt()).isNotNull();
    assertThat(savedWallet.getUpdatedAt()).isNotNull();

    // Verify pessimistic write lock query method
    var lockedWallet =
        walletRepository.findByIdAndUserIdForUpdate(savedWallet.getId(), savedUser.getId());
    assertThat(lockedWallet).isPresent();
  }

  @Test
  @DisplayName("V3 Migration: User has oauth_id column and can query by oauthId")
  void shouldPersistAndQueryUserByOauthId() {
    User user =
        User.builder()
            .fullName("Google Test User")
            .email("google.test@ninepaws.com")
            .authProvider(AuthProvider.GOOGLE)
            .oauthId("google-sub-123456789")
            .emailVerified(true)
            .build();

    userRepository.saveAndFlush(user);

    assertThat(userRepository.findByOauthId("google-sub-123456789")).isPresent();
    assertThat(userRepository.existsByOauthId("google-sub-123456789")).isTrue();
    assertThat(userRepository.findByOauthId("non-existent-sub")).isEmpty();
  }
}
