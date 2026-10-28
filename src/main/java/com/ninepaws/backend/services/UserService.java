package com.ninepaws.backend.services;

import com.ninepaws.backend.dtos.user.LinkGoogleAccountResponse;
import com.ninepaws.backend.dtos.user.UpdateProfileRequest;
import com.ninepaws.backend.dtos.user.UpdateSettingsRequest;
import com.ninepaws.backend.dtos.user.UserProfileResponse;
import com.ninepaws.backend.dtos.user.UserSettingsDto;
import java.util.UUID;

public interface UserService {

  UserProfileResponse getCurrentUserProfile(UUID userId);

  UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);

  UserSettingsDto updateSettings(UUID userId, UpdateSettingsRequest request);

  LinkGoogleAccountResponse linkGoogleAccount(UUID userId, String idToken);
}
