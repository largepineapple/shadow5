package com.shadow.studio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(properties = "studio.initial-delay-ms=60000")
@EnabledIfEnvironmentVariable(named = "MYSQL_INTEGRATION", matches = "true")
class LifecycleIntegrationTest {
  @Autowired TaskLifecycle lifecycle;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  @MockitoSpyBean Store store;
  String id;

  @BeforeEach
  void create() {
    String user = UUID.randomUUID().toString();
    id = UUID.randomUUID().toString();
    new org.springframework.transaction.support.TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              store.user(user, IdentityFilter.hash(user));
              store.create(
                  new Models.Task(
                      id, user, "coast", "QUEUED", null, null, null, null, null, 0, null, null));
              assertEquals(1, store.claim(id));
            });
  }

  @AfterEach
  void clearMocks() {
    reset(store);
  }

  @Test
  void stageAndStepInsertionRollBackTogether() {
    doThrow(new IllegalStateException("step insert unavailable"))
        .when(store)
        .begin(id, "ASSET_CHECK");
    assertThrows(IllegalStateException.class, () -> lifecycle.start(id, "ASSET_CHECK"));
    assertNull(store.lockTask(id).stage());
    assertTrue(store.steps(id).isEmpty());
  }

  @Test
  void failedStepWriteRollsBackTaskFailureAndCanBeRetried() {
    lifecycle.start(id, "ASSET_CHECK");
    doThrow(new IllegalStateException("step update unavailable"))
        .when(store)
        .end(id, "ASSET_CHECK", "FAILED");
    assertThrows(IllegalStateException.class, () -> lifecycle.fail(id, "ASSET_CHECK", "测试失败"));
    assertEquals("RUNNING", store.lockTask(id).status());
    assertEquals("RUNNING", store.steps(id).getFirst().status());

    reset(store);
    lifecycle.fail(id, "ASSET_CHECK", "测试失败");
    assertEquals("FAILED", store.lockTask(id).status());
    assertEquals("FAILED", store.steps(id).getFirst().status());
  }

  @Test
  void terminalTaskCannotRegressOrRunAnotherStep() {
    lifecycle.start(id, "ASSET_CHECK");
    lifecycle.fail(id, "ASSET_CHECK", "测试失败");
    assertThrows(IllegalStateException.class, () -> lifecycle.start(id, "GENERATE"));
    assertEquals("FAILED", store.lockTask(id).status());
  }

  @Test
  void resultMetadataAndSuccessRollbackTogether() {
    lifecycle.start(id, "ASSET_CHECK");
    lifecycle.finishStep(id, "ASSET_CHECK");
    lifecycle.start(id, "GENERATE");
    lifecycle.finishStep(id, "GENERATE");
    lifecycle.start(id, "FINALIZE");
    String user = store.lockTask(id).userId();
    String resultId = UUID.randomUUID().toString();
    Models.Asset result = new Models.Asset(resultId, user, "image/png", 10);
    doThrow(new IllegalStateException("final task update unavailable"))
        .when(store)
        .succeed(id, result.getPreviewUrl());
    assertThrows(IllegalStateException.class, () -> lifecycle.succeed(id, result));
    assertNull(store.assetById(resultId, user));
    assertEquals("RUNNING", store.lockTask(id).status());
    assertEquals("RUNNING", store.steps(id).getLast().status());
    reset(store);
    lifecycle.succeed(id, result);
    assertNotNull(store.assetById(resultId, user));
    assertEquals("SUCCEEDED", store.lockTask(id).status());
    lifecycle.fail(id, "FINALIZE", "late failure");
    assertEquals("SUCCEEDED", store.lockTask(id).status());
  }
}
