package com.shadow.studio;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(Api.class)
class ApiValidationTest {
  @Autowired MockMvc mvc;
  @MockitoBean Store store;
  @MockitoBean FilesService files;
  @MockitoBean TaskService tasks;
  Cookie cookie = new Cookie("studio_session", "test-token");
  String uuid = "b0712753-d790-4fe7-8ed9-4daf89c97a8c";

  @BeforeEach
  void identity() {
    when(store.identity(anyString())).thenReturn("user");
  }

  @Test
  void invalidBodyHasFieldErrorsAndRequestId() throws Exception {
    mvc.perform(
            post("/api/tasks")
                .cookie(cookie)
                .contentType("application/json")
                .content("{\"templateId\":\" \",\"assetIds\":[null],\"delayMs\":-1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.templateId").exists())
        .andExpect(jsonPath("$.requestId").isString())
        .andExpect(header().exists("X-Request-ID"));
    verifyNoInteractions(tasks);
  }

  @Test
  void malformedUnknownAndFractionalParametersAreRejected() throws Exception {
    for (String body :
        new String[] {
          "{",
          "{\"templateId\":\"coast\",\"assetIds\":[\"bad-id\"]}",
          "{\"templateId\":\"coast\",\"assetIds\":[\"" + uuid + "\"],\"delayMs\":1.5}",
          "{\"templateId\":\"coast\",\"assetIds\":[\"" + uuid + "\"],\"unknown\":true}"
        }) {
      mvc.perform(post("/api/tasks").cookie(cookie).contentType("application/json").content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("HTTP_400"))
          .andExpect(jsonPath("$.fieldErrors").isMap());
    }
    verifyNoInteractions(tasks);
  }

  @Test
  void invalidPaginationAndPathAreBadRequests() throws Exception {
    for (String query :
        new String[] {"limit=0", "limit=101", "offset=-1", "offset=1000001", "limit=abc"})
      mvc.perform(get("/api/tasks?" + query).cookie(cookie)).andExpect(status().isBadRequest());
    mvc.perform(get("/api/tasks/not-a-uuid").cookie(cookie)).andExpect(status().isBadRequest());
  }

  @Test
  void frameworkErrorsPreserveStatusAndHeaders() throws Exception {
    mvc.perform(put("/api/tasks").cookie(cookie))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().exists("Allow"))
        .andExpect(jsonPath("$.code").value("HTTP_405"));
    mvc.perform(post("/api/tasks").cookie(cookie).contentType("text/plain").content("hello"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("HTTP_415"));
  }

  @Test
  void authAndOriginErrorsShareContract() throws Exception {
    mvc.perform(get("/api/tasks"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.requestId").isString())
        .andExpect(jsonPath("$.fieldErrors").isMap());
    mvc.perform(post("/api/tasks").cookie(cookie).header("Origin", "https://untrusted.example"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ORIGIN_FORBIDDEN"));
  }

  @Test
  void responseDoesNotExposeInternalIdentityOrSimulationControls() throws Exception {
    var now = LocalDateTime.now();
    when(tasks.create(eq("user"), any()))
        .thenReturn(
            new Models.Task(
                uuid,
                "private-user",
                "coast",
                "QUEUED",
                null,
                null,
                null,
                null,
                "GENERATE",
                999,
                now,
                now));
    mvc.perform(
            post("/api/tasks")
                .cookie(cookie)
                .contentType("application/json")
                .content("{\"templateId\":\"coast\",\"assetIds\":[\"" + uuid + "\"]}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.id").value(uuid))
        .andExpect(jsonPath("$.userId").doesNotExist())
        .andExpect(jsonPath("$.failAt").doesNotExist())
        .andExpect(jsonPath("$.delayMs").doesNotExist());
  }
}
