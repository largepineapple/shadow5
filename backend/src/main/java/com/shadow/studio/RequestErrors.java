package com.shadow.studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestErrors extends OncePerRequestFilter {
  public record ErrorBody(
      String code, String message, Map<String, String> fieldErrors, String requestId) {}

  private final ObjectMapper json;

  public RequestErrors(ObjectMapper json) {
    this.json = json;
  }

  static ErrorBody body(
      HttpServletRequest req, String code, String message, Map<String, String> fields) {
    return new ErrorBody(code, message, fields, (String) req.getAttribute("requestId"));
  }

  public void send(
      HttpServletRequest req, HttpServletResponse res, int status, String code, String message)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/json;charset=UTF-8");
    json.writeValue(res.getOutputStream(), body(req, code, message, Map.of()));
  }

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    if (!req.getRequestURI().startsWith("/api/")) {
      chain.doFilter(req, res);
      return;
    }
    String id = UUID.randomUUID().toString();
    req.setAttribute("requestId", id);
    res.setHeader("X-Request-ID", id);
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Content-Type-Options", "nosniff");
    try {
      chain.doFilter(req, res);
    } catch (Exception error) {
      org.slf4j.LoggerFactory.getLogger(RequestErrors.class).error("Request {} failed", id, error);
      if (res.isCommitted()) throw new ServletException(error);
      send(req, res, 500, "INTERNAL_ERROR", "服务暂时不可用，请稍后重试");
    }
  }
}
