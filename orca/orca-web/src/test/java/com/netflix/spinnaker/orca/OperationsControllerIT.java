/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.orca;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.InMemoryExecutionRepository;
import com.netflix.spinnaker.orca.q.pending.InMemoryPendingExecutionService;
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:orca-test.yml",
      "keiko.queue.redis.enabled=false",
      "redis.enabled=false",
      "services.fiat.enabled=false"
    })
@AutoConfigureMockMvc
class OperationsControllerIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ExecutionRepository executionRepository;

  @Test
  void postOrchestrateTriggersExecutionAndPersistsPipeline() throws Exception {
    Map<String, Object> pipelineRequest =
        Map.of(
            "application",
            "testapp",
            "name",
            "integration-test-pipeline",
            "stages",
            List.of(
                Map.of(
                    "type", "wait",
                    "name", "wait stage",
                    "waitTime", 1)),
            "trigger",
            Map.of("type", "manual"));

    MvcResult mvcResult =
        mockMvc
            .perform(
                post("/orchestrate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(Header.USER.getHeader(), "rahul-chekuri")
                    .content(objectMapper.writeValueAsString(pipelineRequest)))
            .andExpect(status().isOk())
            .andReturn();

    Map<String, Object> response =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), new TypeReference<>() {});

    String ref = (String) response.get("ref");
    assertThat(ref).startsWith("/pipelines/");

    String pipelineId = ref.substring(ref.lastIndexOf('/') + 1);

    var execution = executionRepository.retrieve(PIPELINE, pipelineId);

    assertThat(execution).isNotNull();
    assertThat(execution.getId()).isEqualTo(pipelineId);
    assertThat(execution.getApplication()).isEqualTo("testapp");
    assertThat(execution.getName()).isEqualTo("integration-test-pipeline");

    assertThat(execution.getStages()).hasSize(1);
    assertThat(execution.getStages().get(0).getType()).isEqualTo("wait");
    assertThat(execution.getTrigger().getType()).isEqualTo("manual");
    assertThat(execution.getAuthentication().getUser()).isEqualTo("rahul-chekuri");
  }

  @TestConfiguration
  static class OperationsControllerITConfiguration {

    @Bean
    @Primary
    ExecutionRepository executionRepository() {
      return new InMemoryExecutionRepository();
    }

    @Bean
    PendingExecutionService pendingExecutionService() {
      return new InMemoryPendingExecutionService();
    }
  }
}
