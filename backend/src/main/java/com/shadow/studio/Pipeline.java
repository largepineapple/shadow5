package com.shadow.studio;

import com.shadow.studio.Models.*;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Pipeline {
  static final List<String> STAGES = List.of("ASSET_CHECK", "GENERATE", "FINALIZE");
  private static final Logger log = LoggerFactory.getLogger(Pipeline.class);
  private final Store store;
  private final FilesService files;
  private final TaskLifecycle lifecycle;
  private PendingFailure pendingFailure;

  private record PendingFailure(String id, String stage, String message, Exception cause) {}

  public Pipeline(Store store, FilesService files, TaskLifecycle lifecycle) {
    this.store = store;
    this.files = files;
    this.lifecycle = lifecycle;
  }

  @PostConstruct
  void recover() {
    lifecycle.recover();
  }

  // ponytail: one worker per instance; database leasing is required before horizontal scaling.
  @Scheduled(fixedDelay = 500, initialDelayString = "${studio.initial-delay-ms:0}")
  public void tick() {
    if (pendingFailure != null && !persistFailure()) return;
    Task task = store.next();
    if (task != null && store.claim(task.id()) == 1) run(task);
  }

  void pause(int ms) throws InterruptedException {
    Thread.sleep(ms);
  }

  void run(Task task) {
    String stage = null;
    Asset result = null;
    try {
      for (String current : STAGES) {
        stage = current;
        lifecycle.start(task.id(), stage);
        pause(task.delayMs());
        if (stage.equals(task.failAt())) throw new SimulationFailure("模拟失败：" + stage + "，请重新创建任务");
        if (stage.equals("ASSET_CHECK")) {
          List<Asset> inputs = store.inputs(task.id());
          if (inputs.isEmpty()) throw new SimulationFailure("制作素材不可用");
          for (Asset asset : inputs)
            if (!Files.isRegularFile(files.path(asset.id())))
              throw new SimulationFailure("制作素材文件已丢失");
        }
        if (stage.equals("FINALIZE")) {
          result = files.result(task.userId());
          lifecycle.succeed(task.id(), result);
        } else lifecycle.finishStep(task.id(), stage);
      }
    } catch (Exception error) {
      if (error instanceof InterruptedException) Thread.currentThread().interrupt();
      log.error("Task {} failed during {}", task.id(), stage, error);
      if (result != null) files.discard(result.id());
      pendingFailure =
          new PendingFailure(
              task.id(),
              stage,
              error instanceof SimulationFailure ? error.getMessage() : "制作过程发生异常，请重新创建任务",
              error);
      persistFailure();
    }
  }

  private boolean persistFailure() {
    PendingFailure failure = pendingFailure;
    try {
      lifecycle.fail(failure.id(), failure.stage(), failure.message());
      pendingFailure = null;
      return true;
    } catch (RuntimeException persistenceError) {
      log.error(
          "Cannot persist failure for task {}; worker paused until database recovers. Original"
              + " failure: {}",
          failure.id(),
          failure.cause().toString(),
          persistenceError);
      return false;
    }
  }

  private static class SimulationFailure extends RuntimeException {
    SimulationFailure(String message) {
      super(message);
    }
  }
}
