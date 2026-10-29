package com.shadow.studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class FilesServiceTest {
  @TempDir Path root;

  @Test
  void rejectsTextDisguisedAsPng() throws Exception {
    Store store = mock(Store.class);
    FilesService files = new FilesService(store, root.toString());
    assertThrows(
        ResponseStatusException.class,
        () ->
            files.upload(
                "user",
                new MockMultipartFile("file", "fake.png", "image/png", "not a png".getBytes())));
    verifyNoInteractions(store);
  }

  @Test
  void renderedResultIsDecodableBeforeMetadataTransaction() throws Exception {
    Store store = mock(Store.class);
    FilesService files = new FilesService(store, root.toString());
    var asset = files.result("user");
    assertNotNull(javax.imageio.ImageIO.read(files.path(asset.id()).toFile()));
    verifyNoInteractions(store);
  }

  @Test
  void storageRejectsTraversal() throws Exception {
    FilesService files = new FilesService(mock(Store.class), root.toString());
    assertThrows(IllegalArgumentException.class, () -> files.path("../outside"));
    assertThrows(IllegalArgumentException.class, () -> files.path(null));
  }
}
