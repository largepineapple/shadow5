package com.shadow.studio;

import com.shadow.studio.Models.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Each method is one short transaction. Rendering and simulated waits run outside it. */
@Service
public class TaskLifecycle {
  private final Store store;

  public TaskLifecycle(Store store) {
    this.store = store;
  }

  @Transactional
  public void recover() {
    store.interruptedSteps();
    store.interrupted();
  }

  @Transactional
  public void start(String id, String stage) {
    running(id);
    List<Step> steps = store.steps(id);
    int index = Pipeline.STAGES.indexOf(stage);
    if (index < 0
        || steps.size() != index
        || steps.stream().anyMatch(s -> !s.status().equals("SUCCEEDED")))
      throw new IllegalStateException("Unexpected task stage transition");
    if (store.stage(id, stage) != 1) throw new IllegalStateException("Task is no longer running");
    store.begin(id, stage);
  }

  @Transactional
  public void finishStep(String id, String stage) {
    requireStage(running(id), stage);
    if (store.end(id, stage, "SUCCEEDED") != 1)
      throw new IllegalStateException("Step is no longer running");
  }

  @Transactional
  public void succeed(String id, Asset result) {
    requireStage(running(id), "FINALIZE");
    store.asset(result);
    if (store.end(id, "FINALIZE", "SUCCEEDED") != 1
        || store.succeed(id, result.getPreviewUrl()) != 1)
      throw new IllegalStateException("Cannot complete task");
  }

  @Transactional
  public void fail(String id, String stage, String message) {
    Task task = store.lockTask(id);
    if (task == null || !task.status().equals("RUNNING")) return;
    // Update task first; a step-write failure rolls both writes back for a later persistence
    // attempt.
    store.fail(id, stage, message);
    if (task.stage() != null) store.end(id, task.stage(), "FAILED");
  }

  private Task running(String id) {
    Task task = store.lockTask(id);
    if (task == null || !task.status().equals("RUNNING"))
      throw new IllegalStateException("Task is no longer running");
    return task;
  }

  private void requireStage(Task task, String stage) {
    if (!stage.equals(task.stage())) throw new IllegalStateException("Unexpected task stage");
  }
}
