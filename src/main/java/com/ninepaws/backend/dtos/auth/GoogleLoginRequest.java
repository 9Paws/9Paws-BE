package com.ninepaws.backend.dtos.auth;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
    @NotBlank(message = "idToken không được để trống") String idToken) {}
