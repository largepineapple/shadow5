package com.shadow.studio;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class IdentityFilter extends OncePerRequestFilter {
  private final Store store;
  private final RequestErrors errors;
  private final String origin;
  private final boolean secure;

  public IdentityFilter(
      Store store,
      RequestErrors errors,
      @Value("${studio.origin}") String origin,
      @Value("${studio.secure-cookie}") boolean secure) {
    this.store = store;
    this.errors = errors;
    this.origin = origin;
    this.secure = secure;
  }

  static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (!req.getRequestURI().startsWith("/api/")) {
      chain.doFilter(req, res);
      return;
    }
    if (!Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod())
        && req.getHeader("Origin") != null
        && !origin.equals(req.getHeader("Origin"))) {
      errors.send(req, res, 403, "ORIGIN_FORBIDDEN", "请求来源不受信任");
      return;
    }
    String token = null;
    if (req.getCookies() != null)
      for (Cookie cookie : req.getCookies())
        if (cookie.getName().equals("studio_session")) token = cookie.getValue();
    String user = token == null ? null : store.identity(hash(token));
    if (user == null) {
      if (!req.getRequestURI().equals("/api/session")) {
        errors.send(req, res, 401, "SESSION_REQUIRED", "请刷新页面以恢复身份");
        return;
      }
      token = UUID.randomUUID() + "." + UUID.randomUUID();
      user = UUID.randomUUID().toString();
      store.user(user, hash(token));
      res.addHeader(
          "Set-Cookie",
          ResponseCookie.from("studio_session", token)
              .httpOnly(true)
              .secure(secure)
              .sameSite("Lax")
              .path("/")
              .maxAge(60L * 60 * 24 * 365)
              .build()
              .toString());
    }
    req.setAttribute("user", user);
    res.setHeader("Cache-Control", "no-store");
    chain.doFilter(req, res);
  }
}
