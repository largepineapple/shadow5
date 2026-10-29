package com.shadow.studio;

import static org.mockito.Mockito.*;

import com.shadow.studio.Models.Task;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PipelineTest {
  private Task task(String failure) {
    return new Task(
        "task", "user", "postcard", "QUEUED", null, null, null, null, failure, 0, null, null);
  }

  @Test
  void executesAllStepsInOrder() throws Exception {
    Store store = mock(Store.class);
    FilesService files = mock(FilesService.class);
    when(files.result("user")).thenReturn(new Models.Asset("result", "user", "image/png", 10));
    new Pipeline(store, files).run(task(null));
    InOrder order = inOrder(store);
    for (String stage : Pipeline.STAGES) {
      order.verify(store).stage("task", stage);
      order.verify(store).begin("task", stage);
      if (stage.equals("ASSET_CHECK")) order.verify(store).inputs("task");
      order.verify(store).end("task", stage, "SUCCEEDED");
    }
    order.verify(store).succeed("task", "/api/assets/result/content");
    verify(store, never()).fail(anyString(), anyString());
  }

  @Test
  void eachFailureStopsEveryFollowingStep() {
    for (int i = 0; i < Pipeline.STAGES.size(); i++) {
      Store store = mock(Store.class);
      FilesService files = mock(FilesService.class);
      String stage = Pipeline.STAGES.get(i);
      new Pipeline(store, files).run(task(stage));
      verify(store).fail(eq("task"), contains(stage));
      verify(store).end("task", stage, "FAILED");
      verify(store, never()).succeed(anyString(), anyString());
      for (int j = i + 1; j < Pipeline.STAGES.size(); j++)
        verify(store, never()).begin("task", Pipeline.STAGES.get(j));
    }
  }

  @Test
  void onlyQueuedTasksCanBeClaimed() {
    Store store = mock(Store.class);
    when(store.next()).thenReturn(task(null));
    when(store.claim("task")).thenReturn(0);
    new Pipeline(store, mock(FilesService.class)).tick();
    verify(store, never()).begin(anyString(), anyString());
  }
}
