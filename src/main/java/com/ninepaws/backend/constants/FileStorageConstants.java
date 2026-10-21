package com.ninepaws.backend.constants;

import java.util.List;

public final class FileStorageConstants {

  private FileStorageConstants() {
    // Utility class: prevent instantiation
  }

  public static final String DEFAULT_AVATAR_URL =
      "https://res.cloudinary.com/demo/image/upload/sample.jpg";

  public static final List<String> ALLOWED_IMAGE_TYPES =
      List.of("image/jpeg", "image/png", "image/webp", "image/jpg");

  public static final List<String> ALLOWED_VIDEO_TYPES = List.of("video/mp4", "video/webm");

  public static final long MAX_AVATAR_SIZE_BYTES = 5 * 1024 * 1024L; // 5MB

  public static final long MAX_THUMBNAIL_SIZE_BYTES = 1 * 1024 * 1024L; // 1MB

  public static final String MAX_FILE_SIZE = "10MB";

  public static final String MAX_REQUEST_SIZE = "50MB";
}
