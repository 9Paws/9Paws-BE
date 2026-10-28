package com.ninepaws.backend.security.oauth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.ninepaws.backend.exceptions.BaseAppException;
import com.ninepaws.backend.exceptions.error.AuthErrorCode;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GoogleTokenVerifierImpl implements GoogleTokenVerifier {

  private final GoogleIdTokenVerifier verifier;

  // Package-private constructor for unit testing with mocks
  GoogleTokenVerifierImpl(GoogleIdTokenVerifier verifier) {
    this.verifier = verifier;
  }

  @Autowired
  public GoogleTokenVerifierImpl(
      @Value("${app.oauth.google.client-id:${GOOGLE_CLIENT_ID:}}") String googleClientId) {
    try {
      GoogleIdTokenVerifier.Builder builder =
          new GoogleIdTokenVerifier.Builder(
                  GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance());

      if (googleClientId != null && !googleClientId.isBlank()) {
        builder.setAudience(Collections.singletonList(googleClientId));
      } else {
        log.warn(
            "Google Client ID is not configured! Google ID token audience verification is skipped in current profile.");
      }

      this.verifier = builder.build();
    } catch (GeneralSecurityException | IOException e) {
      log.error("Failed to initialize GoogleIdTokenVerifier", e);
      throw new IllegalStateException("Could not initialize Google ID Token Verifier", e);
    }
  }

  @Override
  public GoogleTokenPayload verify(String idTokenString) {
    if (idTokenString == null || idTokenString.isBlank()) {
      throw new BaseAppException(AuthErrorCode.INVALID_GOOGLE_TOKEN);
    }

    try {
      GoogleIdToken idToken = verifier.verify(idTokenString);
      if (idToken == null) {
        log.warn("Google token verification failed: returned null");
        throw new BaseAppException(AuthErrorCode.INVALID_GOOGLE_TOKEN);
      }

      GoogleIdToken.Payload payload = idToken.getPayload();
      Boolean emailVerified = payload.getEmailVerified();
      if (emailVerified == null || !emailVerified) {
        log.warn("Google token email is unverified: {}", payload.getEmail());
        throw new BaseAppException(AuthErrorCode.INVALID_GOOGLE_TOKEN);
      }

      String sub = payload.getSubject();
      String email = payload.getEmail();
      String name = (String) payload.get("name");
      String picture = (String) payload.get("picture");

      return new GoogleTokenPayload(sub, email, name, picture, true);
    } catch (BaseAppException e) {
      throw e;
    } catch (Exception e) {
      log.error("Exception occurred while verifying Google ID token", e);
      throw new BaseAppException(AuthErrorCode.INVALID_GOOGLE_TOKEN);
    }
  }
}
