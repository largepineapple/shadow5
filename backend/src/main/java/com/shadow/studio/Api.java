package com.shadow.studio;

import com.shadow.studio.Models.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class Api {
  private final Store store;
  private final FilesService files;
  private final TaskService tasks;

  public Api(Store store, FilesService files, TaskService tasks) {
    this.store = store;
    this.files = files;
    this.tasks = tasks;
  }

  private String user(HttpServletRequest req) {
    return (String) req.getAttribute("user");
  }

  @PostMapping("/session")
  public Map<String, String> session(HttpServletRequest req) {
    return Map.of("id", user(req));
  }

  @GetMapping("/templates")
  public List<Template> templates(@RequestParam(required = false) String category) {
    return store.templates().stream()
        .filter(t -> category == null || category.equals(t.category()))
        .toList();
  }

  @GetMapping("/templates/{id}")
  public Template template(@PathVariable @Pattern(regexp = Models.TEMPLATE_PATTERN) String id) {
    Template t = store.template(id);
    if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    return t;
  }

  @PostMapping("/assets")
  public AssetView upload(HttpServletRequest req, @RequestParam MultipartFile file)
      throws IOException {
    return AssetView.of(files.upload(user(req), file));
  }

  @GetMapping("/assets/{id}/content")
  public ResponseEntity<Resource> content(
      HttpServletRequest req, @PathVariable @Pattern(regexp = Models.UUID_PATTERN) String id) {
    Asset a = store.assetById(id, user(req));
    if (a == null || !java.nio.file.Files.isRegularFile(files.path(id)))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "素材不存在");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(a.mimeType()))
        .header("X-Content-Type-Options", "nosniff")
        .body(new FileSystemResource(files.path(id)));
  }

  @PostMapping("/tasks")
  public ResponseEntity<TaskView> create(
      HttpServletRequest req, @Valid @RequestBody CreateTask input) {
    return ResponseEntity.status(202).body(TaskView.of(tasks.create(user(req), input)));
  }

  @GetMapping("/tasks")
  public List<TaskView> history(
      HttpServletRequest req,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int offset,
      @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
    return store.history(user(req), limit, offset).stream().map(TaskView::of).toList();
  }

  @GetMapping("/tasks/{id}")
  public Map<String, Object> task(
      HttpServletRequest req, @PathVariable @Pattern(regexp = Models.UUID_PATTERN) String id) {
    Task t = store.task(id, user(req));
    if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
    return Map.of("task", TaskView.of(t), "steps", store.steps(id));
  }
}
