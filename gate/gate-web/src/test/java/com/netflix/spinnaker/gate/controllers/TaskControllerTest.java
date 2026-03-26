/*
 * Copyright 2026 Salesforce, Inc.
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
package com.netflix.spinnaker.gate.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.netflix.spinnaker.kork.common.Header.USER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = Main.class)
@TestPropertySource(properties = {"spring.config.location=classpath:gate-test.yml"})
class TaskControllerTest {

  private MockMvc webAppMockMvc;

  @RegisterExtension
  static WireMockExtension wmOrca =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired
  @Qualifier("authenticatedRequestFilter")
  private FilterRegistrationBean filterRegistrationBean;

  @MockBean ClouddriverServiceSelector clouddriverServiceSelector;

  @MockBean ClouddriverService clouddriverService;

  /** Prevents the background application cache refresh from running during tests. */
  @MockBean ApplicationService applicationService;

  /** To prevent periodic calls to service's /health endpoints */
  @MockBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  private static final String USERNAME = "some-user";
  private static final String TASK_ID = "test-task-id";

  @DynamicPropertySource
  static void registerUrls(DynamicPropertyRegistry registry) {
    System.out.println("wiremock orca url: " + wmOrca.baseUrl());
    registry.add("services.orca.base-url", wmOrca::baseUrl);
  }

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    webAppMockMvc =
        webAppContextSetup(webApplicationContext)
            .addFilters(filterRegistrationBean.getFilter())
            .build();
  }

  @Test
  void deleteTaskWithEmptyOrcaResponse() throws Exception {
    // Orca's DELETE /tasks/{id} returns void, so the response body is empty.
    stubOrcaGetTask();
    wmOrca.stubFor(
        WireMock.delete(urlPathEqualTo("/tasks/" + TASK_ID))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withResponseBody(Body.none())));

    webAppMockMvc
        .perform(
            delete("/tasks/" + TASK_ID)
                .header(USER.getHeader(), USERNAME)
                .characterEncoding(StandardCharsets.UTF_8.toString()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().string(""));
  }

  @Test
  void cancelTaskWithEmptyOrcaResponse() throws Exception {
    // Orca's PUT /tasks/{id}/cancel returns void, so the response body is empty.
    stubOrcaGetTask();
    wmOrca.stubFor(
        WireMock.put(urlPathEqualTo("/tasks/" + TASK_ID + "/cancel"))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withResponseBody(Body.none())));

    webAppMockMvc
        .perform(
            put("/tasks/" + TASK_ID + "/cancel")
                .header(USER.getHeader(), USERNAME)
                .characterEncoding(StandardCharsets.UTF_8.toString()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().string(""));
  }

  @Test
  void cancelTasksWithEmptyOrcaResponse() throws Exception {
    // Orca's PUT /tasks/cancel returns void, so the response body is empty.
    stubOrcaGetTask();
    wmOrca.stubFor(
        WireMock.put(urlPathEqualTo("/tasks/cancel"))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withResponseBody(Body.none())));

    webAppMockMvc
        .perform(
            put("/tasks/cancel")
                .param("ids", TASK_ID)
                .header(USER.getHeader(), USERNAME)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.toString()))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().string(""));
  }

  /**
   * Stub Orca's GET /tasks/{id} endpoint. TaskService.setApplicationForTask calls this before
   * delete/cancel operations.
   */
  private void stubOrcaGetTask() {
    String taskJson =
        "{\"id\":\"" + TASK_ID + "\",\"status\":\"RUNNING\",\"application\":\"testapp\"}";
    wmOrca.stubFor(
        WireMock.get(urlPathEqualTo("/tasks/" + TASK_ID))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", "application/json")
                    .withBody(taskJson)));
  }
}
