package com.shadow.studio;

import java.time.LocalDateTime;
import java.util.List;

public class Models {
  public record Template(
      String id,
      String title,
      String category,
      String description,
      String coverUrl,
      int requiredImageCount) {
    public List<String> getAcceptedMimeTypes() {
      return List.of("image/jpeg", "image/png");
    }

    public long getMaxFileSizeBytes() {
      return 5242880;
    }
  }

  public record Asset(String id, String userId, String mimeType, long sizeBytes) {
    public String getPreviewUrl() {
      return "/api/assets/" + id + "/content";
    }
  }

  public record Task(
      String id,
      String userId,
      String templateId,
      String status,
      String stage,
      String failedStage,
      String error,
      String resultUrl,
      String failAt,
      int delayMs,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {}

  public record Step(
      String stage, String status, LocalDateTime startedAt, LocalDateTime finishedAt) {}

  public record CreateTask(
      String templateId, List<String> assetIds, String failAt, Integer delayMs) {}
}
