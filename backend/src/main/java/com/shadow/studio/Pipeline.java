package com.shadow.studio;

import com.shadow.studio.Models.*;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Pipeline {
  static final List<String> STAGES = List.of("ASSET_CHECK", "GENERATE", "FINALIZE");
  private final Store store;
  private final FilesService files;

  public Pipeline(Store store, FilesService files) {
    this.store = store;
    this.files = files;
  }

  @PostConstruct
  void recover() {
    store.interruptedSteps();
    store.interrupted();
  }

  // ponytail: single-instance worker; use database leasing before horizontal scaling.
  @Scheduled(fixedDelay = 500)
  public void tick() {
    Task task = store.next();
    if (task != null && store.claim(task.id()) == 1) run(task);
  }

  void pause(int ms) throws InterruptedException {
    Thread.sleep(ms);
  }

  void run(Task task) {
    String stage = null;
    try {
      for (String current : STAGES) {
        stage = current;
        store.stage(task.id(), stage);
        store.begin(task.id(), stage);
        pause(task.delayMs());
        if (stage.equals(task.failAt()))
          throw new IllegalStateException("模拟失败：" + stage + "，请重新创建任务");
        if (stage.equals("ASSET_CHECK")) {
          for (Asset a : store.inputs(task.id()))
            if (!Files.isRegularFile(files.path(a.id())))
              throw new IllegalStateException("制作素材文件已丢失");
        }
        if (stage.equals("FINALIZE")) {
          Asset result = files.result(task.userId());
          store.end(task.id(), stage, "SUCCEEDED");
          store.succeed(task.id(), result.getPreviewUrl());
        } else store.end(task.id(), stage, "SUCCEEDED");
      }
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      if (stage != null) store.end(task.id(), stage, "FAILED");
      store.fail(
          task.id(), e instanceof IllegalStateException ? e.getMessage() : "制作过程发生异常，请重新创建任务");
    }
  }
}
