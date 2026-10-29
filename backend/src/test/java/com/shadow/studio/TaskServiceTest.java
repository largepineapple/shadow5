package com.shadow.studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TaskServiceTest {
  @Test
  void rejectsWrongCountAndForeignAssetsBeforeCreatingTask() {
    Store store = mock(Store.class);
    FilesService files = mock(FilesService.class);
    when(store.template("coast"))
        .thenReturn(new Models.Template("coast", "title", "category", "description", "cover", 1));
    TaskService service = new TaskService(store, files, true);
    assertThrows(
        ResponseStatusException.class,
        () -> service.create("owner", new Models.CreateTask("coast", List.of(), null, 0)));
    assertThrows(
        ResponseStatusException.class,
        () -> service.create("other", new Models.CreateTask("coast", List.of("foreign"), null, 0)));
    verify(store, never()).create(any());
  }

  @Test
  void caseVariantsCannotBypassDistinctAssetValidation() {
    Store store = mock(Store.class);
    when(store.template("postcard"))
        .thenReturn(
            new Models.Template("postcard", "title", "category", "description", "cover", 2));
    String id = "b0712753-d790-4fe7-8ed9-4daf89c97a8c";
    TaskService service = new TaskService(store, mock(FilesService.class), true);
    var input =
        new Models.CreateTask(
            "postcard", List.of(id, id.toUpperCase(java.util.Locale.ROOT)), null, 0);
    assertThrows(ResponseStatusException.class, () -> service.create("owner", input));
    verify(store, never()).create(any());
  }
}
