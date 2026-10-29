package com.shadow.studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MYSQL_INTEGRATION", matches = "true")
class MysqlIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  Cookie session() throws Exception {
    return mvc.perform(post("/api/session"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("studio_session");
  }

  @Test
  void databaseBackedWorkflowAndOwnership() throws Exception {
    Cookie owner = session(), other = session();
    assertNotNull(owner);
    assertNotNull(other);
    mvc.perform(get("/api/templates").cookie(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(6));
    byte[] png =
        java.util.Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    var upload =
        mvc.perform(
                multipart("/api/assets")
                    .file(new MockMultipartFile("file", "sample.png", "image/png", png))
                    .cookie(owner))
            .andExpect(status().isOk())
            .andReturn();
    String asset = json.readTree(upload.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(get("/api/assets/" + asset + "/content").cookie(other))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/tasks")
                .cookie(other)
                .contentType("application/json")
                .content("{\"templateId\":\"coast\",\"assetIds\":[\"" + asset + "\"]}"))
        .andExpect(status().isBadRequest());
    for (String failure : new String[] {"", "ASSET_CHECK", "GENERATE", "FINALIZE"}) {
      String body =
          "{\"templateId\":\"coast\",\"assetIds\":[\""
              + asset
              + "\"],\"delayMs\":0"
              + (failure.isEmpty() ? "" : ",\"failAt\":\"" + failure + "\"")
              + "}";
      var created =
          mvc.perform(
                  post("/api/tasks").cookie(owner).contentType("application/json").content(body))
              .andExpect(status().isAccepted())
              .andReturn();
      String id = json.readTree(created.getResponse().getContentAsString()).get("id").asText();
      mvc.perform(get("/api/tasks/" + id).cookie(other)).andExpect(status().isNotFound());
      com.fasterxml.jackson.databind.JsonNode result = null;
      for (int attempt = 0; attempt < 100; attempt++) {
        result =
            json.readTree(
                mvc.perform(get("/api/tasks/" + id).cookie(owner))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
        String status = result.get("task").get("status").asText();
        if (status.equals("SUCCEEDED") || status.equals("FAILED")) break;
        Thread.sleep(100);
      }
      assertEquals(
          failure.isEmpty() ? "SUCCEEDED" : "FAILED", result.get("task").get("status").asText());
      if (failure.isEmpty()) {
        mvc.perform(get(result.get("task").get("resultUrl").asText()).cookie(owner))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"));
        assertEquals(3, result.get("steps").size());
      } else {
        assertEquals(failure, result.get("task").get("failedStage").asText());
        assertEquals(Pipeline.STAGES.indexOf(failure) + 1, result.get("steps").size());
      }
      mvc.perform(get("/api/tasks/" + id).cookie(owner))
          .andExpect(jsonPath("$.task.status").value(failure.isEmpty() ? "SUCCEEDED" : "FAILED"));
    }
  }
}
