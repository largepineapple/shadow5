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
}
