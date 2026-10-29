package com.shadow.studio;

import java.util.List;

final class ImagePolicy {
  static final long MAX_BYTES = 5L * 1024 * 1024;
  static final long MAX_PIXELS = 24_000_000;
  static final List<String> MIME_TYPES = List.of("image/jpeg", "image/png");

  private ImagePolicy() {}
}
