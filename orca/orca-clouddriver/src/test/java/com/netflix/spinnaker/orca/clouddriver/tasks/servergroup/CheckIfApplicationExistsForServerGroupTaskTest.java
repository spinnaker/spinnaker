/*
 * Copyright 2021 Salesforce.com, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import static com.netflix.spinnaker.orca.TestUtils.getResourceAsStream;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.config.TaskConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CreateServerGroupStage;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.Application;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import retrofit.client.Response;
import retrofit.mime.TypedByteArray;

public class CheckIfApplicationExistsForServerGroupTaskTest {
  private @Nullable Front50Service front50Service;
  private OortService oortService;
  private ObjectMapper objectMapper;
  private RetrySupport retrySupport;
  private CheckIfApplicationExistsForServerGroupTask task;
  private Application front50Application;
  private TaskConfigurationProperties configurationProperties;
  StageExecutionImpl stageExecution;

  @BeforeEach
  public void setup() {
    front50Service = mock(Front50Service.class);
    oortService = mock(OortService.class);
    objectMapper = new ObjectMapper();
    retrySupport = new RetrySupport();
    configurationProperties = new TaskConfigurationProperties();

    front50Application = new Application();
    front50Application.setUser("test-user");
    PipelineExecutionImpl pipeline = new PipelineExecutionImpl(PIPELINE, "1", "testapp");

    // Test Stage
    stageExecution =
        new StageExecutionImpl(
            pipeline, CreateServerGroupStage.PIPELINE_CONFIG_TYPE, "Test Stage", new HashMap<>());
  }

  @DisplayName("parameterized test where front50 is queried for an application")
  @ParameterizedTest(
      name = "{index} ==> when application name is obtained from = {0} key in the context")
  @ValueSource(strings = {"application", "moniker", "serverGroupName", "asgName", "cluster"})
  public void testSuccessfulRetrievalOfApplicationFromFront50(String applicationNameSource) {
    // setup:
    task =
        new CheckIfApplicationExistsForServerGroupTask(
            front50Service, oortService, objectMapper, retrySupport, configurationProperties);

    assert front50Service != null;
    when(front50Service.get("testapp")).thenReturn(front50Application);
    stageExecution.setContext(getStageContext(applicationNameSource));

    // when:
    TaskResult result = task.execute(stageExecution);

    // then:
    assertThat(task.getApplicationName(stageExecution)).isEqualTo("testapp");
    assertThat(front50Application.getUser()).isEqualTo("test-user");
    verify(front50Service).get("testapp");
    assertEquals(result.getStatus(), ExecutionStatus.SUCCEEDED);
    verifyNoInteractions(oortService);
  }

  @DisplayName(
      "parameterized test where clouddriver is queried for an application "
          + "if the application is not found in front50")
  @ParameterizedTest(
      name = "{index} ==> when application name is obtained from = {0} key in the context")
  @ValueSource(strings = {"application", "moniker", "serverGroupName", "asgName", "cluster"})
  public void testSuccessfulRetrievalOfApplicationFromClouddriverIfFront50IsDisabled(
      String applicationNameSource) throws IOException {
    // setup:
    task =
        new CheckIfApplicationExistsForServerGroupTask(
            null, oortService, objectMapper, retrySupport, configurationProperties);

    when(oortService.getApplication("testapp"))
        .thenReturn(
            getApplicationResponse("clouddriver/tasks/servergroup/clouddriver-application.json"));
    stageExecution.setContext(getStageContext(applicationNameSource));

    // when:
    TaskResult result = task.execute(stageExecution);

    // then:
    assertThat(task.getApplicationName(stageExecution)).isEqualTo("testapp");
    verifyNoInteractions(front50Service);
    verify(oortService).getApplication("testapp");
    assertEquals(result.getStatus(), ExecutionStatus.SUCCEEDED);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testIfApplicationCannotBeRetrievedFromFront50AndCheckClouddriverIsFalse(
      boolean auditModeEnabled) {
    TaskConfigurationProperties configurationProperties = new TaskConfigurationProperties();
    configurationProperties.getCheckIfApplicationExistsTask().setCheckClouddriver(false);
    configurationProperties.getCheckIfApplicationExistsTask().setAuditModeEnabled(auditModeEnabled);
    final String expectedErrorMessage = "did not find application: testapp in front50";
    // setup:
    task =
        new CheckIfApplicationExistsForServerGroupTask(
            null, oortService, objectMapper, retrySupport, configurationProperties);

    stageExecution.setContext(getStageContext("application"));

    // then
    if (auditModeEnabled) {
      TaskResult result = task.execute(stageExecution);
      assertEquals(result.getStatus(), ExecutionStatus.SUCCEEDED);
      assertEquals(
          expectedErrorMessage, result.getOutputs().get("checkIfApplicationExistsWarning"));
    } else {
      NotFoundException thrown =
          assertThrows(NotFoundException.class, () -> task.execute(stageExecution));

      assertThat(thrown.getMessage()).contains(expectedErrorMessage);
    }

    verifyNoInteractions(front50Service);
    verifyNoInteractions(oortService);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testAnApplicationWhichDoesNotExistInBothFront50AndClouddriver(
      boolean auditModeEnabled) {
    // setup:
    task =
        new CheckIfApplicationExistsForServerGroupTask(
            null, oortService, objectMapper, retrySupport, configurationProperties);
    configurationProperties.getCheckIfApplicationExistsTask().setAuditModeEnabled(auditModeEnabled);

    final String expectedErrorMessage =
        "did not find application: invalid app in front50 and in clouddriver";
    when(oortService.getApplication("invalid app"))
        .thenReturn(
            new Response(
                "test-url",
                HttpStatus.NOT_FOUND.value(),
                "application does not exist",
                Collections.emptyList(),
                new TypedByteArray("application/json", new byte[0])));

    Map<String, Object> stageContext = new HashMap<>();
    stageContext.put("application", "invalid app");
    stageExecution.setContext(stageContext);
    // then
    if (auditModeEnabled) {
      TaskResult result = task.execute(stageExecution);
      assertEquals(result.getStatus(), ExecutionStatus.SUCCEEDED);
      assertEquals(
          expectedErrorMessage, result.getOutputs().get("checkIfApplicationExistsWarning"));
    } else {
      // then
      NotFoundException thrown =
          assertThrows(NotFoundException.class, () -> task.execute(stageExecution));

      assertThat(thrown.getMessage()).contains(expectedErrorMessage);
    }
    verifyNoInteractions(front50Service);
    verify(oortService).getApplication("invalid app");
  }

  private Map<String, Object> getStageContext(String applicationNameSource) {
    Map<String, Object> stageContext = new HashMap<>();
    switch (applicationNameSource) {
      case "application":
        stageContext.put("application", "testapp");
        break;
      case "moniker":
        stageContext.put("moniker", Map.of("app", "testapp", "stack", "preprod", "detail", "test"));
        break;
      case "serverGroupName":
        stageContext.put("serverGroupName", "testapp-preprod-test-v003");
        break;
      case "asgName":
        stageContext.put("asgName", "testapp-preprod-test-v003");
        break;
      case "cluster":
        stageContext.put("cluster", "testapp-preprod-test");
        break;
    }
    return stageContext;
  }

  private Response getApplicationResponse(String resourceName) throws IOException {
    InputStream jobStatusInputStream = getResourceAsStream(resourceName);

    return new Response(
        "test-url",
        200,
        "test-reason",
        Collections.emptyList(),
        new TypedByteArray("application/json", IOUtils.toByteArray(jobStatusInputStream)));
  }
}
