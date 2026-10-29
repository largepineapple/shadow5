package com.shadow.studio;

import com.shadow.studio.Models.*;
import java.nio.file.Files;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskService {
  private final Store store;
  private final FilesService files;
  private final boolean demo;

  public TaskService(Store store, FilesService files, @Value("${studio.demo}") boolean demo) {
    this.store = store;
    this.files = files;
    this.demo = demo;
  }

  @Transactional
  public Task create(String user, CreateTask input) {
    if (input == null || input.templateId() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择模板");
    Template template = store.template(input.templateId());
    if (template == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    if (input.assetIds() == null
        || input.assetIds().size() != template.requiredImageCount()
        || new HashSet<>(input.assetIds()).size() != input.assetIds().size())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请按要求上传不重复的图片");
    for (String id : input.assetIds()) {
      Asset a = store.assetById(id, user);
      if (a == null
          || !Files.isRegularFile(files.path(id))
          || a.sizeBytes() > 5242880
          || !Set.of("image/jpeg", "image/png").contains(a.mimeType()))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "素材不可用或不属于当前用户");
    }
    if (!demo && (input.failAt() != null || input.delayMs() != null))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模拟控制未启用");
    if (input.failAt() != null && !Pipeline.STAGES.contains(input.failAt()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无效的失败阶段");
    int delay = input.delayMs() == null ? 2500 : input.delayMs();
    if (delay < 0 || delay > 30000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每阶段耗时应为 0～30000 毫秒");
    String id = UUID.randomUUID().toString();
    store.create(
        new Task(
            id,
            user,
            input.templateId(),
            "QUEUED",
            null,
            null,
            null,
            null,
            input.failAt(),
            delay,
            null,
            null));
    for (int i = 0; i < input.assetIds().size(); i++) store.attach(id, input.assetIds().get(i), i);
    return store.task(id, user);
  }
}
