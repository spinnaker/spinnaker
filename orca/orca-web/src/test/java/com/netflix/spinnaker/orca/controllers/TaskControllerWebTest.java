/*
 * Copyright 2024 Salesforce, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.controllers;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.netflix.spinnaker.orca.Main;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = Main.class)
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:orca-test.yml",
      "keiko.queue.redis.enabled=false"
    })
class TaskControllerWebTest {

  private static final String TEST_EXECUTION_ID = "12345";

  private static final String TEST_STAGE_ID = "stageId";

  @Autowired private WebApplicationContext webApplicationContext;

  @MockBean private ExecutionRepository executionRepository;

  @MockBean private PendingExecutionService pendingExecutionService;

  @MockBean private NotificationClusterLock notificationClusterLock;

  private MockMvc webAppMockMvc;

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    webAppMockMvc = webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
  }

  @Test
  void testFailedStages() throws Exception {
    webAppMockMvc
        .perform(
            get("/pipelines/failedStages")
                .queryParam("executionId", TEST_EXECUTION_ID)
                .characterEncoding(StandardCharsets.UTF_8.toString()))
        .andDo(print())
        .andExpect(status().isOk());

    // verify PreAuthorize behavior
    verify(executionRepository).getApplication(TEST_EXECUTION_ID);

    // verify implementation behavior
    verify(executionRepository).retrieve(PIPELINE, TEST_EXECUTION_ID, true);
    verifyNoMoreInteractions(executionRepository);
  }

  @Test
  void testDeletePipeline() throws Exception {
    PipelineExecution execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, "test");
    execution.setStatus(ExecutionStatus.SUCCEEDED); // arbitrary
    when(executionRepository.retrieve(PIPELINE, TEST_EXECUTION_ID)).thenReturn(execution);

    webAppMockMvc
        .perform(
            delete("/pipelines/" + TEST_EXECUTION_ID)
                .characterEncoding(StandardCharsets.UTF_8.toString()))
        .andDo(print())
        .andExpect(status().isOk());

    // verify PreAuthorize behavior
    verify(executionRepository).getApplication(TEST_EXECUTION_ID);

    // verify implementation behavior
    verify(executionRepository).retrieve(PIPELINE, TEST_EXECUTION_ID);
    verify(executionRepository).delete(PIPELINE, TEST_EXECUTION_ID);
    verifyNoMoreInteractions(executionRepository);
  }

  @Test
  void testCancel() throws Exception {
    PipelineExecution execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, "test");
    when(executionRepository.retrieve(PIPELINE, TEST_EXECUTION_ID)).thenReturn(execution);

    webAppMockMvc
        .perform(
            put("/pipelines/" + TEST_EXECUTION_ID + "/cancel")
                .characterEncoding(StandardCharsets.UTF_8.toString()))
        .andDo(print())
        .andExpect(status().isAccepted());

    // verify PreAuthorize behavior
    verify(executionRepository).getApplication(TEST_EXECUTION_ID);

    // verify implementation behavior
    verify(executionRepository)
        .cancel(PIPELINE, TEST_EXECUTION_ID, "anonymousUser", null /* reason */);
    verify(executionRepository).retrieve(PIPELINE, TEST_EXECUTION_ID);
    verify(executionRepository).handlesPartition(isNull());
    verifyNoMoreInteractions(executionRepository);
  }

  @Test
  void testPause() throws Exception {
    PipelineExecution execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, "test");
    when(executionRepository.retrieve(PIPELINE, TEST_EXECUTION_ID)).thenReturn(execution);

    webAppMockMvc
        .perform(
            put("/pipelines/" + TEST_EXECUTION_ID + "/pause")
                .characterEncoding(StandardCharsets.UTF_8.toString()))
        .andDo(print())
        .andExpect(status().isAccepted());

    // verify PreAuthorize behavior
    verify(executionRepository).getApplication(TEST_EXECUTION_ID);

    // verify implementation behavior
    verify(executionRepository).retrieve(PIPELINE, TEST_EXECUTION_ID);
    verify(executionRepository).pause(PIPELINE, TEST_EXECUTION_ID, "anonymousUser");
    verify(executionRepository).handlesPartition(isNull());
    verifyNoMoreInteractions(executionRepository);
  }

  @Test
  void testResume() throws Exception {
    PipelineExecution execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, "test");
    when(executionRepository.retrieve(PIPELINE, TEST_EXECUTION_ID)).thenReturn(execution);

    webAppMockMvc
        .perform(
            put("/pipelines/" + TEST_EXECUTION_ID + "/resume")
                .characterEncoding(StandardCharsets.UTF_8.toString()))
        .andDo(print())
        .andExpect(status().isAccepted());

    // verify PreAuthorize behavior
    verify(executionRepository).getApplication(TEST_EXECUTION_ID);

    // verify implementation behavior
    verify(executionRepository)
        .resume(PIPELINE, TEST_EXECUTION_ID, "anonymousUser", false /* ignoreCurrentStatus */);
    verify(executionRepository).retrieve(PIPELINE, TEST_EXECUTION_ID);
    verify(executionRepository).handlesPartition(isNull());
    verifyNoMoreInteractions(executionRepository);
  }

  @Test
  void testRunning() throws Exception {
    webAppMockMvc
        .perform(get("/pipelines/running").characterEncoding(StandardCharsets.UTF_8.toString()))
        .andDo(print())
        .andExpect(status().isGone());

    verifyNoInteractions(executionRepository);
  }

  @Test
  void testWaiting() throws Exception {
    webAppMockMvc
        .perform(get("/pipelines/waiting").characterEncoding(StandardCharsets.UTF_8.toString()))
        .andDo(print())
        .andExpect(status().isGone());

    verifyNoInteractions(executionRepository);
  }

  @Test
  void testUpdatePipelineStage() throws Exception {
    PipelineExecution execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, "test");
    StageExecution stage = new StageExecutionImpl(execution, "evaluateVariables"); // arbitrary
    stage.setId(TEST_STAGE_ID);
    execution.getStages().add(stage);
    when(executionRepository.retrieve(PIPELINE, TEST_EXECUTION_ID)).thenReturn(execution);

    webAppMockMvc
        .perform(
            patch("/pipelines/" + TEST_EXECUTION_ID + "/stages/" + TEST_STAGE_ID)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.toString())
                .content("{}"))
        .andDo(print())
        .andExpect(status().isOk());

    // verify PreAuthorize behavior
    verify(executionRepository).getApplication(TEST_EXECUTION_ID);

    // verify implementation behavior
    verify(executionRepository, times(2)).retrieve(PIPELINE, TEST_EXECUTION_ID);
    verify(executionRepository).storeStage(any(StageExecution.class));
    verify(executionRepository).handlesPartition(isNull());
    verifyNoMoreInteractions(executionRepository);
  }

  @Test
  void testRetryPipelineStage() throws Exception {
    PipelineExecution execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, "test");
    when(executionRepository.retrieve(PIPELINE, TEST_EXECUTION_ID)).thenReturn(execution);

    webAppMockMvc
        .perform(
            put("/pipelines/" + TEST_EXECUTION_ID + "/stages/" + TEST_STAGE_ID + "/restart")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.toString())
                .content("{}"))
        .andDo(print())
        .andExpect(status().isOk());

    // verify PreAuthorize behavior
    verify(executionRepository).getApplication(TEST_EXECUTION_ID);

    // verify implementation behavior
    verify(executionRepository).retrieve(PIPELINE, TEST_EXECUTION_ID);
    verify(executionRepository).handlesPartition(isNull());
    verify(executionRepository).restartStage(TEST_EXECUTION_ID, TEST_STAGE_ID);
    verifyNoMoreInteractions(executionRepository);
  }

  @Test
  void testEvaluateExpressionForExecution() throws Exception {
    PipelineExecution execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, "test");
    when(executionRepository.retrieve(PIPELINE, TEST_EXECUTION_ID)).thenReturn(execution);

    webAppMockMvc
        .perform(
            get("/pipelines/" + TEST_EXECUTION_ID + "/evaluateExpression")
                .queryParam("expression", "")
                .characterEncoding(StandardCharsets.UTF_8.toString()))
        .andDo(print())
        .andExpect(status().isOk());

    // verify PreAuthorize behavior
    verify(executionRepository).getApplication(TEST_EXECUTION_ID);

    // verify implementation behavior
    verify(executionRepository).retrieve(PIPELINE, TEST_EXECUTION_ID);
    verifyNoMoreInteractions(executionRepository);
  }

  @Test
  void testEvaluateExpressionForExecutionAtStage() throws Exception {
    PipelineExecution execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, "test");
    StageExecution stage = new StageExecutionImpl(execution, "evaluateVariables"); // arbitrary
    stage.setId(TEST_STAGE_ID);
    execution.getStages().add(stage);
    when(executionRepository.retrieve(PIPELINE, TEST_EXECUTION_ID)).thenReturn(execution);

    webAppMockMvc
        .perform(
            get("/pipelines/" + TEST_EXECUTION_ID + "/" + TEST_STAGE_ID + "/evaluateExpression")
                .queryParam("expression", "")
                .characterEncoding(StandardCharsets.UTF_8.toString()))
        .andDo(print())
        .andExpect(status().isOk());

    // verify PreAuthorize behavior
    verify(executionRepository).getApplication(TEST_EXECUTION_ID);

    // verify implementation behavior
    verify(executionRepository).retrieve(PIPELINE, TEST_EXECUTION_ID);
    verifyNoMoreInteractions(executionRepository);
  }

  @Test
  void testEvaluateVariables() throws Exception {
    PipelineExecution execution = new PipelineExecutionImpl(ExecutionType.PIPELINE, "test");
    when(executionRepository.retrieve(PIPELINE, TEST_EXECUTION_ID)).thenReturn(execution);

    webAppMockMvc
        .perform(
            post("/pipelines/" + TEST_EXECUTION_ID + "/evaluateVariables")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.toString())
                .content("[]"))
        .andDo(print())
        .andExpect(status().isOk());

    // verify PreAuthorize behavior
    verify(executionRepository).getApplication(TEST_EXECUTION_ID);

    // verify implementation behavior
    verify(executionRepository).retrieve(PIPELINE, TEST_EXECUTION_ID);
    verifyNoMoreInteractions(executionRepository);
  }
}
