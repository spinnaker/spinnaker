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

package com.netflix.spinnaker.front50.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.config.Front50CoreConfiguration;
import com.netflix.spinnaker.front50.config.controllers.PipelineControllerConfig;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pipeline fields that {@link Pipeline} doesn't declare live in its "any" map, which only
 * Front50ApiModule's Jackson 2 mixin maps to and from top-level JSON properties.
 */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = PipelineController.class)
@ContextConfiguration(
    classes = {
      AuthorizationSupport.class,
      PipelineController.class,
      PipelineControllerConfig.class,
      Front50CoreConfiguration.class
    })
class PipelineControllerSerializationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PipelineDAO pipelineDAO;

  @MockitoBean private FiatPermissionEvaluator fiatPermissionEvaluator;

  @Test
  void returnsUndeclaredFieldsAsTopLevelProperties() throws Exception {
    Pipeline pipeline = new Pipeline();
    pipeline.setId("1");
    pipeline.setName("my-pipeline");
    pipeline.setApplication("my-app");
    pipeline.setAny("description", "deploys my-app");
    when(pipelineDAO.findById("1")).thenReturn(pipeline);

    mockMvc
        .perform(get("/pipelines/1/get"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("deploys my-app"))
        .andExpect(jsonPath("$.any").doesNotExist());
  }

  @Test
  void savesUndeclaredFields() throws Exception {
    when(pipelineDAO.create(any(), any(Pipeline.class)))
        .thenAnswer(invocation -> invocation.getArgument(1));

    mockMvc
        .perform(
            post("/pipelines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"id\":\"1\",\"name\":\"my-pipeline\",\"application\":\"my-app\","
                        + "\"description\":\"deploys my-app\"}"))
        .andExpect(status().isOk());

    ArgumentCaptor<Pipeline> saved = ArgumentCaptor.forClass(Pipeline.class);
    verify(pipelineDAO).create(eq("1"), saved.capture());
    assertThat(saved.getValue().getAny()).containsEntry("description", "deploys my-app");
  }
}
