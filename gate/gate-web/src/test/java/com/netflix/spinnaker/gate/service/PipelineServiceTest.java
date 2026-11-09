/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.gate.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.netflix.spinnaker.kork.common.Header.ACCOUNTS;
import static com.netflix.spinnaker.kork.common.Header.USER;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import retrofit2.mock.Calls;

@SpringBootTest(classes = Main.class)
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "services.echo.enabled=true",
      "services.front50.applicationRefreshInitialDelayMs=3600000"
    })
public class PipelineServiceTest {
  private MockMvc webAppMockMvc;

  @RegisterExtension
  static WireMockExtension wmOrca =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @RegisterExtension
  static WireMockExtension wmEcho =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @RegisterExtension
  static WireMockExtension wmFront50 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired private WebApplicationContext webApplicationContext;

  ObjectMapper objectMapper = new ObjectMapper();

  /**
   * This takes X-SPINNAKER-* headers from requests to gate and puts them in the MDC. This is
   * enabled when gate runs normally (by GateConfig), but needs explicit mention to function in
   * these tests.
   */
  @Autowired
  @Qualifier("authenticatedRequestFilter")
  private FilterRegistrationBean filterRegistrationBean;

  @MockitoBean ClouddriverServiceSelector clouddriverServiceSelector;

  @MockitoBean ClouddriverService clouddriverService;

  /** To prevent periodic calls to service's /health endpoints */
  @MockitoBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  /** To prevent periodic calls to load accounts from clouddriver */
  @MockitoBean DefaultProviderLookupService defaultProviderLookupService;

  private static final String APPLICATION_NAME = "my-application";
  private static final String PIPELINE_NAME = "my-pipeline-name";
  private static final String USERNAME = "some user";
  private static final String ACCOUNT = "my-account";
  private static final String PIPELINE_EXECUTION_ID = "my-pipeline-execution-id";

  @DynamicPropertySource
  static void registerUrls(DynamicPropertyRegistry registry) {
    // Configure wiremock's random ports into gate
    System.out.println("wiremock orca url: " + wmOrca.baseUrl());
    registry.add("services.orca.base-url", wmOrca::baseUrl);
    System.out.println("wiremock echo url: " + wmEcho.baseUrl());
    registry.add("services.echo.base-url", wmEcho::baseUrl);
    registry.add("services.front50.base-url", wmFront50::baseUrl);
  }

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    webAppMockMvc =
        webAppContextSetup(webApplicationContext)
            .addFilters(filterRegistrationBean.getFilter())
            .build();

    // Keep the background thread that refreshes the applications cache in
    // ApplicationService happy.
    when(clouddriverServiceSelector.select()).thenReturn(clouddriverService);
    when(clouddriverService.getAllApplicationsUnrestricted(anyBoolean()))
        .thenReturn(Calls.response(Collections.emptyList()));
  }

  @Test
  void invokeDeletePipelineExecution() throws Exception {
    wmOrca.stubFor(
        WireMock.get(urlEqualTo("/pipelines/" + PIPELINE_EXECUTION_ID))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(objectMapper.writeValueAsString(Map.of("foo", "bar")))));

    // simulate Orca response to the delete request
    wmOrca.stubFor(
        WireMock.delete(urlEqualTo("/pipelines/" + PIPELINE_EXECUTION_ID))
            .willReturn(aResponse().withStatus(200)));

    webAppMockMvc
        .perform(
            delete("/pipelines/" + PIPELINE_EXECUTION_ID)
                .header(
                    USER.getHeader(),
                    USERNAME) // to silence warning when X-SPINNAKER-USER is missing
                .header(
                    ACCOUNTS.getHeader(),
                    ACCOUNT)) // to silence warning when X-SPINNAKER-ACCOUNTS is missing
        .andDo(print())
        .andExpect(status().is2xxSuccessful());
  }

  /** Simulate front50's response to ApplicationService.getPipelineConfigForApplication */
  private void stubPipelineConfig(Map<String, Object> pipelineConfig) throws Exception {
    wmFront50.stubFor(
        WireMock.get(urlPathEqualTo("/pipelines/" + APPLICATION_NAME + "/name/" + PIPELINE_NAME))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(objectMapper.writeValueAsString(pipelineConfig))));
  }

  private RequestBuilder triggerViaEchoRequest(Map<String, Object> trigger) throws Exception {
    return post("/pipelines/v2/" + APPLICATION_NAME + "/" + PIPELINE_NAME)
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .content(objectMapper.writeValueAsString(trigger))
        .header(USER.getHeader(), USERNAME) // to silence warning when X-SPINNAKER-USER is missing
        .header(
            ACCOUNTS.getHeader(),
            ACCOUNT); // to silence warning when X-SPINNAKER-ACCOUNTS is missing
  }

  @Test
  void invokePipelineConfigViaEcho() throws Exception {
    stubPipelineConfig(
        Map.of(
            "name",
            PIPELINE_NAME,
            "parameterConfig",
            List.of(Map.of("name", "param1", "required", true))));
    wmEcho.stubFor(WireMock.post(urlEqualTo("/")).willReturn(aResponse().withStatus(200)));

    webAppMockMvc
        .perform(triggerViaEchoRequest(Map.of("parameters", Map.of("param1", "a"))))
        .andDo(print())
        .andExpect(status().isAccepted());
  }

  @Test
  void invokePipelineConfigViaEchoMissingRequiredParameter() throws Exception {
    stubPipelineConfig(
        Map.of(
            "name",
            PIPELINE_NAME,
            "parameterConfig",
            List.of(Map.of("name", "param1", "required", true))));
    wmEcho.stubFor(WireMock.post(urlEqualTo("/")).willReturn(aResponse().withStatus(200)));
    wmEcho.resetRequests();

    webAppMockMvc
        .perform(triggerViaEchoRequest(Map.of()))
        .andDo(print())
        .andExpect(status().isBadRequest());

    wmEcho.verify(0, WireMock.postRequestedFor(urlEqualTo("/")));
  }
}
