/*
 * Copyright 2023 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.Main;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
      "keiko.queue.redis.enabled = false"
    })
class OperationsControllerTest {

  private MockMvc webAppMockMvc;

  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired ObjectMapper objectMapper;

  @MockBean ExecutionRepository executionRepository;

  @MockBean PendingExecutionService pendingExecutionService;

  @MockBean NotificationClusterLock notificationClusterLock;

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
    webAppMockMvc = webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void orchestrateWithEmptyRequestBody() throws Exception {
    webAppMockMvc
        .perform(
            post("/orchestrate")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.toString()))
        .andDo(print())
        .andExpect(status().isBadRequest());
  }

  @Test
  void orchestrateWithEmptyPipelineMap() throws Exception {
    webAppMockMvc
        .perform(
            post("/orchestrate")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.toString())
                .content(objectMapper.writeValueAsString(Map.of())))
        .andDo(print())
        // It's a little marginal to accept an empty pipeline since it's likely
        // a user error to try to run a pipeline that doesn't do anything.
        .andExpect(status().isOk());
  }

  @Test
  void orchestrateWithoutStages() throws Exception {
    Map<String, Object> pipelineWithoutStages =
        Map.of("application", "my-application", "name", "my-pipeline-name");
    webAppMockMvc
        .perform(
            post("/orchestrate")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.toString())
                .content(objectMapper.writeValueAsString(pipelineWithoutStages)))
        .andDo(print())
        // It's a little marginal to accept a pipeline without stages since it's
        // likely a user error to try to run a pipeline that doesn't do
        // anything.
        .andExpect(status().isOk());
  }

  @Test
  void orchestrateTreatsMissingTypeAsBadRequest() throws Exception {
    Map<String, Object> pipelineWithStageWithoutType =
        Map.of(
            "application",
            "my-application",
            "name",
            "my-pipeline-name",
            "stages",
            List.of(Map.of("name", "my-pipeline-stage-name")));
    webAppMockMvc
        .perform(
            post("/orchestrate")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.toString())
                .content(objectMapper.writeValueAsString(pipelineWithStageWithoutType)))
        .andDo(print())
        .andExpect(status().isBadRequest());
  }
}
