package com.ninepaws.backend.controllers;

import com.ninepaws.backend.dtos.auth.GoogleLoginRequest;
import com.ninepaws.backend.dtos.response.base.SingleResponse;
import com.ninepaws.backend.dtos.user.LinkGoogleAccountResponse;
import com.ninepaws.backend.dtos.user.UpdateProfileRequest;
import com.ninepaws.backend.dtos.user.UpdateSettingsRequest;
import com.ninepaws.backend.dtos.user.UserProfileResponse;
import com.ninepaws.backend.dtos.user.UserSettingsDto;
import com.ninepaws.backend.security.SecurityUtils;
import com.ninepaws.backend.services.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController extends AbstractBaseController {

  private final UserService userService;

  @GetMapping("/me")
  public ResponseEntity<SingleResponse<UserProfileResponse>> getProfile() {
    UUID currentUserId = SecurityUtils.getCurrentUserId();
    UserProfileResponse profile = userService.getCurrentUserProfile(currentUserId);
    return success(profile, "Lấy thông tin người dùng thành công");
  }

  @PutMapping("/me")
  public ResponseEntity<SingleResponse<UserProfileResponse>> updateProfile(
      @Valid @RequestBody UpdateProfileRequest request) {
    UUID currentUserId = SecurityUtils.getCurrentUserId();
    UserProfileResponse updated = userService.updateProfile(currentUserId, request);
    return success(updated, "Cập nhật thông tin người dùng thành công");
  }

  @PatchMapping("/me/settings")
  public ResponseEntity<SingleResponse<UserSettingsDto>> updateSettings(
      @Valid @RequestBody UpdateSettingsRequest request) {
    UUID currentUserId = SecurityUtils.getCurrentUserId();
    UserSettingsDto updated = userService.updateSettings(currentUserId, request);
    return success(updated, "Cập nhật cài đặt thành công");
  }

  @PostMapping("/me/link-google")
  public ResponseEntity<SingleResponse<LinkGoogleAccountResponse>> linkGoogle(
      @Valid @RequestBody GoogleLoginRequest request) {
    UUID currentUserId = SecurityUtils.getCurrentUserId();
    LinkGoogleAccountResponse response =
        userService.linkGoogleAccount(currentUserId, request.idToken());
    return success(response, "Liên kết tài khoản Google thành công");
  }
}
