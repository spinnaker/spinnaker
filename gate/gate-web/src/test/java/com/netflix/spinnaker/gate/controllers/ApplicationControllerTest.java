/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.gate.controllers;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.ErrorConfiguration;
import com.netflix.spinnaker.config.RetrofitErrorConfiguration;
import com.netflix.spinnaker.gate.config.ApplicationConfigurationProperties;
import com.netflix.spinnaker.gate.config.ServiceConfiguration;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.ExecutionHistoryService;
import com.netflix.spinnaker.gate.services.TaskService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import java.util.List;
import java.util.Map;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.mock.Calls;

@EnableWebMvc
@SpringBootTest(
    classes = {
      ApplicationController.class,
      ApplicationService.class,
      ServiceConfiguration.class,
      ErrorConfiguration.class,
      RetrofitErrorConfiguration.class
    })
class ApplicationControllerTest {

  @Autowired private WebApplicationContext webApplicationContext;

  MockMvc mockMvc;

  @MockBean Front50Service front50Service;

  @MockBean ClouddriverServiceSelector clouddriverSelector;

  @MockBean ClouddriverService clouddriver;

  @MockBean ApplicationConfigurationProperties applicationConfigurationProperties;

  @MockBean ExecutionHistoryService executionHistoryService;

  @MockBean TaskService taskService;

  @MockBean PipelineController pipelineController;

  @BeforeEach
  void setup() {
    when(clouddriverSelector.select()).thenReturn(clouddriver);

    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void getPipelineExecutionsForApplicationWithoutParams() throws Exception {
    List<Map<String, Object>> pipelines =
        List.of(
            Map.of("name", "pipelineA", "executionField", "some-random-x"),
            Map.of("name", "pipelineB", "executionField", "some-random-F"));
    when(executionHistoryService.getPipelines("true-app", 10, null, null, null))
        .thenReturn(pipelines);

    ResultActions response =
        mockMvc.perform(get("/applications/true-app/pipelines").accept(MediaType.APPLICATION_JSON));

    verify(executionHistoryService)
        .getPipelines(
            "true-app", 10, null /* statuses */, null /*expand */, null /*pipelineNameFilter */);
    verifyNoMoreInteractions(executionHistoryService);

    response.andExpect(status().isOk());
    response.andExpect(content().string(new ObjectMapper().writeValueAsString(pipelines)));
  }

  @Test
  void getPipelineExecutionsForApplicationWithParams() throws Exception {
    List<Map<String, Object>> pipelines =
        List.of(
            Map.of("name", "pipelineA", "executionField", "some-random-x"),
            Map.of("name", "pipelineB", "executionField", "some-random-F"));

    int limit = 2;
    String statuses = "RUNNING";
    boolean expand = false;
    String pipelineNameFilter = "pipeline";
    when(executionHistoryService.getPipelines(
            "true-app", limit, statuses, expand, pipelineNameFilter))
        .thenReturn(pipelines);

    ResultActions response =
        mockMvc.perform(
            get("/applications/true-app/pipelines")
                .param("limit", Integer.toString(limit))
                .param("statuses", statuses)
                .param("expand", Boolean.toString(expand))
                .param("pipelineNameFilter", pipelineNameFilter)
                .accept(MediaType.APPLICATION_JSON));

    verify(executionHistoryService)
        .getPipelines("true-app", limit, statuses, expand, pipelineNameFilter);
    verifyNoMoreInteractions(executionHistoryService);

    response.andExpect(status().isOk());
    response.andExpect(content().string(new ObjectMapper().writeValueAsString(pipelines)));
  }

  @Test
  void getPipelineConfigsForApplicationWithoutPipelineNameFilter() throws Exception {
    // given: random configs
    List<Map<String, Object>> configs =
        List.of(
            Map.of("name", "pipelineA", "some", "some-random-x"),
            Map.of("name", "pipelineB", "some", "some-random-F"));
    when(front50Service.getPipelineConfigsForApplication("true-app", null, true))
        .thenReturn(Calls.response(configs));

    // when: "all configs are requested"
    ResultActions response =
        mockMvc.perform(
            get("/applications/true-app/pipelineConfigs").accept(MediaType.APPLICATION_JSON));

    // then: "we only call front50 once, and do not pass through the pipelineNameFilter"
    verify(front50Service).getPipelineConfigsForApplication("true-app", null, true);
    verifyNoMoreInteractions(front50Service);

    // and: "we get all configs"
    response.andExpect(status().isOk());
    response.andExpect(content().string(new ObjectMapper().writeValueAsString(configs)));
  }

  @Test
  void getPipelineConfigsForApplicationWithPipelineNameFilter() throws Exception {
    // given: "only one config"
    List<Map<String, Object>> configs =
        List.of(Map.of("name", "pipelineA", "some", "some-random-x"));
    when(front50Service.getPipelineConfigsForApplication("true-app", "pipelineA", true))
        .thenReturn(Calls.response(configs));

    // when: "configs are requested with a filter"
    ResultActions response =
        mockMvc.perform(
            get("/applications/true-app/pipelineConfigs?pipelineNameFilter=pipelineA")
                .accept(MediaType.APPLICATION_JSON));

    // then: "we only call front50 once, and we do pass through the pipelineNameFilter"
    verify(front50Service).getPipelineConfigsForApplication("true-app", "pipelineA", true);
    verifyNoMoreInteractions(front50Service);

    // and: "only filtered configs are returned"
    response.andExpect(status().isOk());
    response.andExpect(content().string(new ObjectMapper().writeValueAsString(configs)));
  }

  @Test
  void getPipelineConfigForExistingPipeline() throws Exception {
    // given:
    Map<String, Object> someTruePipeline =
        Map.of(
            "name", "some-true-pipeline",
            "some", "some-random-x");
    when(front50Service.getPipelineConfigByApplicationAndName(
            "true-app", "some-true-pipeline", true))
        .thenReturn(Calls.response(someTruePipeline));

    // when:
    ResultActions response =
        mockMvc.perform(
            get("/applications/true-app/pipelineConfigs/some-true-pipeline")
                .accept(MediaType.APPLICATION_JSON));

    // then:
    verify(front50Service)
        .getPipelineConfigByApplicationAndName("true-app", "some-true-pipeline", true);
    verifyNoMoreInteractions(front50Service);
    response.andExpect(status().isOk());
    response.andExpect(content().string(new ObjectMapper().writeValueAsString(someTruePipeline)));
  }

  @Test
  void getPipelineConfigForMissingPipeline() throws Exception {
    // given:
    // ApplicationService.getPipelineConfigForApplication queries front50 initially by name.  On a
    // 404 response, it queries front50 again by id, so respond with 404 from that as well.
    when(front50Service.getPipelineConfigByApplicationAndName(
            "true-app", "some-fake-pipeline", true))
        .thenThrow(makeSpinnakerHttpException(404));
    when(front50Service.getPipelineConfigById("some-fake-pipeline"))
        .thenThrow(makeSpinnakerHttpException(404));

    // when:
    ResultActions response =
        mockMvc.perform(
            get("/applications/true-app/pipelineConfigs/some-fake-pipeline")
                .accept(MediaType.APPLICATION_JSON));

    // then:
    verify(front50Service)
        .getPipelineConfigByApplicationAndName("true-app", "some-fake-pipeline", true);
    verify(front50Service).getPipelineConfigById("some-fake-pipeline");
    verifyNoMoreInteractions(front50Service);

    // and:
    response.andExpect(status().isNotFound());
    response.andExpect(
        status()
            .reason(
                "Pipeline configuration not found (nameOrId: some-fake-pipeline in application true-app): Status: 404, Method: GET, URL: http://localhost/, Message: arbitrary message"));
  }

  @Test
  void getPipelineConfigWithFront50Error() throws Exception {
    // given:
    when(front50Service.getPipelineConfigByApplicationAndName(
            "true-app", "some-fake-pipeline", true))
        .thenThrow(makeSpinnakerHttpException(500));

    // when:
    ResultActions response =
        mockMvc.perform(
            get("/applications/true-app/pipelineConfigs/some-fake-pipeline")
                .accept(MediaType.APPLICATION_JSON));

    // then:
    verify(front50Service)
        .getPipelineConfigByApplicationAndName("true-app", "some-fake-pipeline", true);
    verifyNoMoreInteractions(front50Service);

    // and:
    response.andExpect(status().isInternalServerError());
    response.andExpect(
        status()
            .reason(
                "Status: 500, Method: GET, URL: http://localhost/, Message: arbitrary message"));
  }

  @Test
  void getStrategyConfigForExistingStrategy() throws Exception {
    // given:
    List<Map<String, Object>> configs =
        List.of(
            Map.of(
                "name", "some-true-strategy",
                "some", "some-random-x",
                "someY", "some-random-y"),
            Map.of(
                "name", "some-fake-strategy",
                "some", "some-random-F",
                "someY", "some-random-Z"));
    when(front50Service.getStrategyConfigs("true-app")).thenReturn(Calls.response(configs));

    // when:
    ResultActions response =
        mockMvc.perform(
            get("/applications/true-app/strategyConfigs/some-true-strategy")
                .accept(MediaType.APPLICATION_JSON));

    // then:
    verify(front50Service).getStrategyConfigs("true-app");
    verifyNoMoreInteractions(front50Service);
    response.andExpect(status().isOk());
    response.andExpect(content().string(new ObjectMapper().writeValueAsString(configs.get(0))));
  }

  @Test
  void getStrategyConfigsForMissingStrategy() throws Exception {
    // given:
    List<Map<String, Object>> configs =
        List.of(
            Map.of(
                "name", "some-true-strategy",
                "some", "some-random-x",
                "someY", "some-random-y"));
    when(front50Service.getStrategyConfigs("true-app")).thenReturn(Calls.response(configs));

    // when:
    ResultActions response =
        mockMvc.perform(
            get("/applications/true-app/strategyConfigs/some-fake-strategy")
                .accept(MediaType.APPLICATION_JSON));

    // then:
    verify(front50Service).getStrategyConfigs("true-app");
    verifyNoMoreInteractions(front50Service);

    // and:
    response.andExpect(status().isNotFound());
    response.andExpect(
        status()
            .reason(
                "Strategy config (id: some-fake-strategy) not found for Application (id: true-app)"));
  }

  static SpinnakerHttpException makeSpinnakerHttpException(int status) {
    String url = "https://front50";
    Response<String> retrofit2Response =
        Response.error(
            status,
            ResponseBody.create(
                okhttp3.MediaType.parse("application/json"),
                "{ \"message\": \"arbitrary message\" }"));

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit);
  }
}
