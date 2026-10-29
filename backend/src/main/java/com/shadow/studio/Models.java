package com.shadow.studio;

import jakarta.validation.constraints.*;
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
      return ImagePolicy.MIME_TYPES;
    }

    public long getMaxFileSizeBytes() {
      return ImagePolicy.MAX_BYTES;
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

  static final String UUID_PATTERN =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
  static final String TEMPLATE_PATTERN = "[a-z0-9][a-z0-9-]{0,39}";

  public record CreateTask(
      @NotBlank(message = "请选择模板") @Pattern(regexp = TEMPLATE_PATTERN, message = "模板 ID 格式不正确")
          String templateId,
      @NotNull(message = "请上传图片") @Size(min = 1, max = 10, message = "素材数量应为 1～10 张")
          List<
                  @NotBlank(message = "素材 ID 不能为空")
                  @Pattern(regexp = UUID_PATTERN, message = "素材 ID 格式不正确") String>
              assetIds,
      @Pattern(regexp = "ASSET_CHECK|GENERATE|FINALIZE", message = "无效的失败阶段") String failAt,
      @Min(value = 0, message = "耗时不能小于 0") @Max(value = 30000, message = "耗时不能超过 30000 毫秒")
          Integer delayMs) {}

  public record TaskView(
      String id,
      String templateId,
      String status,
      String stage,
      String failedStage,
      String error,
      String resultUrl,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    static TaskView of(Task task) {
      return new TaskView(
          task.id(),
          task.templateId(),
          task.status(),
          task.stage(),
          task.failedStage(),
          task.error(),
          task.resultUrl(),
          task.createdAt(),
          task.updatedAt());
    }
  }

  public record AssetView(String id, String mimeType, long sizeBytes, String previewUrl) {
    static AssetView of(Asset asset) {
      return new AssetView(asset.id(), asset.mimeType(), asset.sizeBytes(), asset.getPreviewUrl());
    }
  }
}
