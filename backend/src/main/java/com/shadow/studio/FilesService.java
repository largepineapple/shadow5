package com.shadow.studio;

import com.shadow.studio.Models.Asset;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FilesService {
  private final Path root;
  private final Store store;

  public FilesService(Store store, @Value("${studio.storage}") String root) throws IOException {
    this.store = store;
    this.root = Path.of(root).toAbsolutePath();
    Files.createDirectories(this.root);
  }

  public Path path(String id) {
    if (id == null || !id.matches(Models.UUID_PATTERN))
      throw new IllegalArgumentException("Invalid storage ID");
    return root.resolve(id);
  }

  public Asset upload(String user, MultipartFile file) throws IOException {
    if (file.isEmpty() || file.getSize() > ImagePolicy.MAX_BYTES)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择不超过 5MB 的图片");
    String mime;
    try (var input = file.getInputStream();
        var in = ImageIO.createImageInputStream(input)) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext())
        throw new ResponseStatusException(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, "仅支持有效的 JPEG 或 PNG 图片");
      ImageReader reader = readers.next();
      try {
        reader.setInput(in);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!Set.of("jpeg", "png").contains(format))
          throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "仅支持 JPEG 或 PNG");
        if ((long) reader.getWidth(0) * reader.getHeight(0) > ImagePolicy.MAX_PIXELS)
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片不得超过 2400 万像素");
        if (reader.read(0) == null) throw new IOException("Invalid image");
        mime = "image/" + format;
      } finally {
        reader.dispose();
      }
    } catch (ResponseStatusException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片损坏，无法读取");
    }
    String id = UUID.randomUUID().toString();
    try {
      file.transferTo(path(id));
    } catch (IOException | RuntimeException error) {
      discard(id);
      throw error;
    }
    Asset asset = new Asset(id, user, mime, file.getSize());
    try {
      store.asset(asset);
    } catch (RuntimeException e) {
      discard(id);
      throw e;
    }
    return asset;
  }

  public void discard(String id) {
    try {
      Files.deleteIfExists(path(id));
    } catch (IOException cleanupError) {
      org.slf4j.LoggerFactory.getLogger(FilesService.class)
          .warn("Cannot remove orphan file {}", id, cleanupError);
    }
  }

  public Asset result(String user) throws IOException {
    String id = UUID.randomUUID().toString();
    var image =
        new java.awt.image.BufferedImage(1200, 800, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var g = image.createGraphics();
    try {
      g.setColor(new java.awt.Color(225, 239, 237));
      g.fillRect(0, 0, 1200, 800);
      g.setColor(new java.awt.Color(43, 101, 105));
      g.fillOval(750, 70, 260, 260);
      g.setColor(new java.awt.Color(100, 152, 145));
      g.fillPolygon(
          new int[] {0, 500, 850, 1200, 1200, 0}, new int[] {650, 280, 570, 380, 800, 800}, 6);
      g.setColor(java.awt.Color.WHITE);
      g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 64));
      g.drawString("A MOMENT TO KEEP", 70, 150);
      g.setFont(new java.awt.Font("SansSerif", 0, 26));
      g.drawString("SHADOW STUDIO / SIMULATED ARTWORK", 75, 205);
    } finally {
      g.dispose();
    }
    try {
      if (!ImageIO.write(image, "png", path(id).toFile()))
        throw new IOException("PNG encoder unavailable");
    } catch (IOException | RuntimeException error) {
      discard(id);
      throw error;
    }
    try {
      return new Asset(id, user, "image/png", Files.size(path(id)));
    } catch (IOException | RuntimeException error) {
      discard(id);
      throw error;
    }
  }
}
