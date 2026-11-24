/*
 * Copyright 2026 Harness, Inc.
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.InMemoryExecutionRepository;
import com.netflix.spinnaker.orca.q.pending.InMemoryPendingExecutionService;
import com.netflix.spinnaker.orca.q.pending.PendingExecutionService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:orca-test.yml",
      "keiko.queue.redis.enabled=false",
      "redis.enabled=false",
      "services.fiat.enabled=false"
    })
@AutoConfigureMockMvc
class ExecutionResponseSerializationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ExecutionRepository executionRepository;

  @Test
  void pipelineResponseUsesOrcaObjectMapper() throws Exception {
    PipelineExecutionImpl pipeline = new PipelineExecutionImpl(PIPELINE, "testapp");
    DefaultTrigger trigger = new DefaultTrigger("webhook");
    trigger.setOther("payload", Map.of("ref", "refs/heads/main"));
    pipeline.setTrigger(trigger);
    executionRepository.store(pipeline);

    mockMvc
        .perform(get("/pipelines/{id}", pipeline.getId()))
        .andExpect(status().isOk())
        // TriggerMixin's @JsonAnyGetter flattens Trigger.getOther() into the trigger.
        .andExpect(jsonPath("$.trigger.payload.ref").value("refs/heads/main"))
        .andExpect(jsonPath("$.trigger.other").doesNotExist())
        // OrcaObjectMapper omits null properties.
        .andExpect(jsonPath("$.endTime").doesNotExist());
  }

  @TestConfiguration
  static class ExecutionResponseSerializationTestConfiguration {

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
