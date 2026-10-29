package com.shadow.studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.shadow.studio.Models.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PipelineTest {
  Task task(String failure) {
    return new Task(
        "task", "user", "postcard", "QUEUED", null, null, null, null, failure, 0, null, null);
  }

  Store store() {
    Store store = mock(Store.class);
    when(store.inputs("task")).thenReturn(List.of(new Asset("input", "user", "image/png", 10)));
    return store;
  }

  FilesService files() {
    FilesService files = mock(FilesService.class);
    when(files.path("input")).thenReturn(Path.of("pom.xml"));
    return files;
  }

  @Test
  void executesStagesInOrder() throws Exception {
    Store store = store();
    FilesService files = files();
    TaskLifecycle lifecycle = mock(TaskLifecycle.class);
    when(files.result("user")).thenReturn(new Asset("result", "user", "image/png", 10));
    // Use an existing source file independently of the Maven working directory.
    when(files.path("input")).thenReturn(Path.of(System.getProperty("java.home"), "release"));
    new Pipeline(store, files, lifecycle).run(task(null));
    InOrder order = inOrder(lifecycle);
    for (String stage : Pipeline.STAGES) {
      order.verify(lifecycle).start("task", stage);
      if (!stage.equals("FINALIZE")) order.verify(lifecycle).finishStep("task", stage);
    }
    order.verify(lifecycle).succeed(eq("task"), any());
    verify(lifecycle, never()).fail(anyString(), anyString(), anyString());
  }

  @Test
  void everyFailureStopsFollowingStages() {
    for (int i = 0; i < Pipeline.STAGES.size(); i++) {
      Store store = store();
      FilesService files = files();
      when(files.path("input")).thenReturn(Path.of(System.getProperty("java.home"), "release"));
      TaskLifecycle lifecycle = mock(TaskLifecycle.class);
      String stage = Pipeline.STAGES.get(i);
      new Pipeline(store, files, lifecycle).run(task(stage));
      verify(lifecycle).fail(eq("task"), eq(stage), contains(stage));
      verify(lifecycle, never()).succeed(anyString(), any());
      for (int j = i + 1; j < Pipeline.STAGES.size(); j++)
        verify(lifecycle, never()).start("task", Pipeline.STAGES.get(j));
    }
  }

  @Test
  void failurePersistenceRetriesBeforeClaimingAnotherTask() {
    Store store = store();
    TaskLifecycle lifecycle = mock(TaskLifecycle.class);
    doThrow(new IllegalStateException("database offline"))
        .doNothing()
        .when(lifecycle)
        .fail(eq("task"), eq("ASSET_CHECK"), anyString());
    Pipeline pipeline = new Pipeline(store, files(), lifecycle);
    assertDoesNotThrow(() -> pipeline.run(task("ASSET_CHECK")));
    verify(store, never()).next();
    pipeline.tick();
    InOrder order = inOrder(lifecycle, store);
    order.verify(lifecycle).start("task", "ASSET_CHECK");
    order.verify(lifecycle, times(2)).fail(eq("task"), eq("ASSET_CHECK"), anyString());
    order.verify(store).next();
  }

  @Test
  void failedResultCommitCleansFileAndStops() {
    Store store = store();
    FilesService files = files();
    when(files.path("input")).thenReturn(Path.of(System.getProperty("java.home"), "release"));
    TaskLifecycle lifecycle = mock(TaskLifecycle.class);
    try {
      when(files.result("user")).thenReturn(new Asset("result", "user", "image/png", 10));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
    doThrow(new IllegalStateException("transaction failed"))
        .when(lifecycle)
        .succeed(anyString(), any());
    new Pipeline(store, files, lifecycle).run(task(null));
    verify(files).discard("result");
    verify(lifecycle).fail("task", "FINALIZE", "制作过程发生异常，请重新创建任务");
  }

  @Test
  void cannotRunUnclaimedTask() {
    Store store = mock(Store.class);
    when(store.next()).thenReturn(task(null));
    new Pipeline(store, files(), mock(TaskLifecycle.class)).tick();
    verify(store).claim("task");
    verify(store).next();
  }
}
