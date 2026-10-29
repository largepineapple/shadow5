package com.shadow.studio;

import com.shadow.studio.Models.*;
import jakarta.servlet.http.HttpServletRequest;
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
  public Template template(@PathVariable String id) {
    Template t = store.template(id);
    if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在");
    return t;
  }

  @PostMapping("/assets")
  public Asset upload(HttpServletRequest req, @RequestParam MultipartFile file) throws IOException {
    return files.upload(user(req), file);
  }

  @GetMapping("/assets/{id}/content")
  public ResponseEntity<Resource> content(HttpServletRequest req, @PathVariable String id) {
    Asset a = store.assetById(id, user(req));
    if (a == null || !java.nio.file.Files.isRegularFile(files.path(id)))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "素材不存在");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(a.mimeType()))
        .header("X-Content-Type-Options", "nosniff")
        .body(new FileSystemResource(files.path(id)));
  }

  @PostMapping("/tasks")
  public ResponseEntity<Task> create(HttpServletRequest req, @RequestBody CreateTask input) {
    return ResponseEntity.status(202).body(tasks.create(user(req), input));
  }

  @GetMapping("/tasks")
  public List<Task> history(
      HttpServletRequest req,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "30") int limit) {
    return store.history(user(req), Math.clamp(limit, 1, 100), Math.max(offset, 0));
  }

  @GetMapping("/tasks/{id}")
  public Map<String, Object> task(HttpServletRequest req, @PathVariable String id) {
    Task t = store.task(id, user(req));
    if (t == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
    return Map.of("task", t, "steps", store.steps(id));
  }
}

@RestControllerAdvice
class ApiErrors {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> known(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(
            Map.of(
                "code",
                e.getStatusCode().value(),
                "message",
                Objects.requireNonNullElse(e.getReason(), "请求失败")));
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<?> large() {
    return ResponseEntity.status(413).body(Map.of("message", "图片不能超过 5MB"));
  }

  @ExceptionHandler({
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.multipart.support.MissingServletRequestPartException.class
  })
  ResponseEntity<?> invalid() {
    return ResponseEntity.badRequest().body(Map.of("message", "请求格式不正确，请检查参数"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> other(Exception e) {
    org.slf4j.LoggerFactory.getLogger(ApiErrors.class).error("API request failed", e);
    return ResponseEntity.internalServerError().body(Map.of("message", "服务暂时不可用，请稍后重试"));
  }
}
