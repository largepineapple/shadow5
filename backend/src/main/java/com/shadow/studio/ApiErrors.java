package com.shadow.studio;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {
  private ResponseEntity<Object> response(
      WebRequest request,
      HttpStatusCode status,
      HttpHeaders headers,
      String message,
      Map<String, String> fields) {
    HttpServletRequest servlet = ((ServletWebRequest) request).getRequest();
    String code = "HTTP_" + status.value();
    return new ResponseEntity<>(
        RequestErrors.body(servlet, code, message, fields), headers, status);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException error,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> fields = new TreeMap<>();
    error
        .getBindingResult()
        .getFieldErrors()
        .forEach(
            field ->
                fields.putIfAbsent(
                    field.getField(),
                    Objects.requireNonNullElse(field.getDefaultMessage(), "参数不合法")));
    return response(request, status, headers, "请检查请求参数", fields);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception error,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String message =
        switch (status.value()) {
          case 400 -> "请求格式或参数不正确";
          case 404 -> "请求资源不存在";
          case 405 -> "请求方法不受支持";
          case 413 -> "图片不能超过 5MB";
          case 415 -> "请求内容类型不受支持";
          default -> "请求无法处理";
        };
    return response(request, status, headers, message, Map.of());
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Object> known(ResponseStatusException error, WebRequest request) {
    return response(
        request,
        error.getStatusCode(),
        error.getHeaders(),
        Objects.requireNonNullElse(error.getReason(), "请求失败"),
        Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> unexpected(Exception error, WebRequest request) {
    HttpServletRequest servlet = ((ServletWebRequest) request).getRequest();
    logger.error("Request " + servlet.getAttribute("requestId") + " failed", error);
    return response(
        request, HttpStatus.INTERNAL_SERVER_ERROR, new HttpHeaders(), "服务暂时不可用，请稍后重试", Map.of());
  }
}
