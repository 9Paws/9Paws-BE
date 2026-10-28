package com.ninepaws.backend.security.oauth;

public interface GoogleTokenVerifier {

  GoogleTokenPayload verify(String idToken);
}
