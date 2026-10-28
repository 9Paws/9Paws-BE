package com.ninepaws.backend.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.ninepaws.backend.exceptions.BaseAppException;
import com.ninepaws.backend.exceptions.error.AuthErrorCode;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleTokenVerifierTest {

  private GoogleIdTokenVerifier mockInternalVerifier;
  private GoogleTokenVerifierImpl googleTokenVerifier;

  @BeforeEach
  void setUp() {
    mockInternalVerifier = mock(GoogleIdTokenVerifier.class);
    googleTokenVerifier = new GoogleTokenVerifierImpl(mockInternalVerifier);
  }

  @Test
  @DisplayName("Should successfully verify valid Google ID token and extract payload")
  void shouldVerifyValidToken() throws GeneralSecurityException, IOException {
    String rawToken = "valid.google.id.token";
    GoogleIdToken idToken = mock(GoogleIdToken.class);
    GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
    payload.setSubject("google-sub-987654321");
    payload.setEmail("mai.nguyen@gmail.com");
    payload.setEmailVerified(true);
    payload.set("name", "Mai Nguyễn");
    payload.set("picture", "https://lh3.googleusercontent.com/avatar.jpg");

    when(idToken.getPayload()).thenReturn(payload);
    when(mockInternalVerifier.verify(rawToken)).thenReturn(idToken);

    GoogleTokenPayload result = googleTokenVerifier.verify(rawToken);

    assertThat(result).isNotNull();
    assertThat(result.sub()).isEqualTo("google-sub-987654321");
    assertThat(result.email()).isEqualTo("mai.nguyen@gmail.com");
    assertThat(result.name()).isEqualTo("Mai Nguyễn");
    assertThat(result.picture()).isEqualTo("https://lh3.googleusercontent.com/avatar.jpg");
    assertThat(result.emailVerified()).isTrue();
  }

  @Test
  @DisplayName("Should reject when internal verifier returns null (invalid or expired token)")
  void shouldRejectInvalidToken() throws GeneralSecurityException, IOException {
    String rawToken = "invalid.token";
    when(mockInternalVerifier.verify(rawToken)).thenReturn(null);

    assertThatThrownBy(() -> googleTokenVerifier.verify(rawToken))
        .isInstanceOf(BaseAppException.class)
        .extracting(ex -> ((BaseAppException) ex).getErrorCode())
        .isEqualTo(AuthErrorCode.INVALID_GOOGLE_TOKEN);
  }

  @Test
  @DisplayName("Should reject when Google token email is not verified")
  void shouldRejectUnverifiedEmail() throws GeneralSecurityException, IOException {
    String rawToken = "unverified.email.token";
    GoogleIdToken idToken = mock(GoogleIdToken.class);
    GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
    payload.setSubject("google-sub-unverified");
    payload.setEmail("unverified@gmail.com");
    payload.setEmailVerified(false);

    when(idToken.getPayload()).thenReturn(payload);
    when(mockInternalVerifier.verify(rawToken)).thenReturn(idToken);

    assertThatThrownBy(() -> googleTokenVerifier.verify(rawToken))
        .isInstanceOf(BaseAppException.class)
        .extracting(ex -> ((BaseAppException) ex).getErrorCode())
        .isEqualTo(AuthErrorCode.INVALID_GOOGLE_TOKEN);
  }

  @Test
  @DisplayName("Should reject when verifier throws SecurityException or IOException")
  void shouldRejectOnVerifierException() throws GeneralSecurityException, IOException {
    String rawToken = "exceptional.token";
    when(mockInternalVerifier.verify(rawToken)).thenThrow(new IOException("Network error"));

    assertThatThrownBy(() -> googleTokenVerifier.verify(rawToken))
        .isInstanceOf(BaseAppException.class)
        .extracting(ex -> ((BaseAppException) ex).getErrorCode())
        .isEqualTo(AuthErrorCode.INVALID_GOOGLE_TOKEN);
  }
}
