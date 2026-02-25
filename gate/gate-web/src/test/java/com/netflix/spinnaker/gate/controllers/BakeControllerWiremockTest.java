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
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.netflix.spinnaker.kork.common.Header.ACCOUNTS;
import static com.netflix.spinnaker.kork.common.Header.USER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/** WireMock-based tests for BakeController endpoints that call rosco via BakeService. */
@SpringBootTest(classes = Main.class)
@TestPropertySource(
    properties = {"spring.config.location=classpath:gate-test.yml", "services.rosco.enabled=true"})
class BakeControllerWiremockTest {

  private MockMvc webAppMockMvc;

  @RegisterExtension
  static WireMockExtension wmRosco =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired private WebApplicationContext webApplicationContext;

  /**
   * This takes X-SPINNAKER-* headers from requests to gate and puts them in the MDC. From there,
   * SpinnakerRequestHeaderInterceptor includes them in outgoing requests (e.g. to rosco). This
   * filter is enabled when gate runs normally (by GateConfig), but needs explicit mention to
   * function in these tests.
   */
  @Autowired
  @Qualifier("authenticatedRequestFilter")
  private FilterRegistrationBean filterRegistrationBean;

  /**
   * To prevent refreshing the applications cache, which involves calls to clouddriver and front50.
   */
  @MockBean ApplicationService applicationService;

  /** To prevent calls to clouddriver */
  @MockBean DefaultProviderLookupService defaultProviderLookupService;

  /** To prevent periodic calls to service's /health endpoints */
  @MockBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  private static final String USERNAME = "some user";
  private static final String ACCOUNT = "my-account";

  @DynamicPropertySource
  static void registerUrls(DynamicPropertyRegistry registry) {
    System.out.println("wiremock rosco url: " + wmRosco.baseUrl());
    registry.add("services.rosco.base-url", wmRosco::baseUrl);
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
  void bakeOptionsSuccess() throws Exception {
    wmRosco.stubFor(
        WireMock.get(urlPathEqualTo("/bakeOptions"))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", "application/json")
                    .withBody("[{\"cloudProvider\":\"aws\",\"baseImages\":[]}]")));

    webAppMockMvc
        .perform(
            get("/bakery/options")
                .header(USER.getHeader(), USERNAME)
                .header(ACCOUNTS.getHeader(), ACCOUNT))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].cloudProvider").value("aws"))
        .andExpect(jsonPath("$[0].baseImages").isArray());

    wmRosco.verify(1, getRequestedFor(urlPathEqualTo("/bakeOptions")));
  }

  @Test
  void bakeOptionsForCloudProviderSuccess() throws Exception {
    wmRosco.stubFor(
        WireMock.get(urlPathEqualTo("/bakeOptions/aws"))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"cloudProvider\":\"aws\",\"baseImages\":[]}")));

    webAppMockMvc
        .perform(
            get("/bakery/options/aws")
                .header(USER.getHeader(), USERNAME)
                .header(ACCOUNTS.getHeader(), ACCOUNT))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cloudProvider").value("aws"))
        .andExpect(jsonPath("$.baseImages").isArray());

    wmRosco.verify(1, getRequestedFor(urlPathEqualTo("/bakeOptions/aws")));
  }

  @Test
  void lookupLogsSuccess() throws Exception {
    wmRosco.stubFor(
        WireMock.get(urlPathEqualTo("/api/v1/us-east-1/logs/some-status-id"))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"logsContent\":\"some logs\"}")));

    // BakeService.lookupLogs returns a plain (i.e. not json) String
    webAppMockMvc
        .perform(
            get("/bakery/logs/us-east-1/some-status-id")
                .header(USER.getHeader(), USERNAME)
                .header(ACCOUNTS.getHeader(), ACCOUNT))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().string("\"<pre>some logs</pre>\""));

    wmRosco.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/us-east-1/logs/some-status-id")));
  }

  @Test
  void bakeOptionsRoscoHttpError() throws Exception {
    wmRosco.stubFor(
        WireMock.get(urlPathEqualTo("/bakeOptions"))
            .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));

    webAppMockMvc
        .perform(
            get("/bakery/options")
                .header(USER.getHeader(), USERNAME)
                .header(ACCOUNTS.getHeader(), ACCOUNT))
        .andDo(print())
        .andExpect(status().isInternalServerError())
        .andExpect(
            status()
                .reason(
                    "Status: 500, Method: GET, URL: "
                        + wmRosco.baseUrl()
                        + "/bakeOptions, Message: Server Error"));

    wmRosco.verify(1, getRequestedFor(urlPathEqualTo("/bakeOptions")));
  }

  @Test
  void lookupLogsRoscoHttpError() throws Exception {
    wmRosco.stubFor(
        WireMock.get(urlPathEqualTo("/api/v1/us-east-1/logs/some-status-id"))
            .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));

    webAppMockMvc
        .perform(
            get("/bakery/logs/us-east-1/some-status-id")
                .header(USER.getHeader(), USERNAME)
                .header(ACCOUNTS.getHeader(), ACCOUNT))
        .andDo(print())
        .andExpect(status().isInternalServerError())
        .andExpect(
            status()
                .reason(
                    "Status: 500, Method: GET, URL: "
                        + wmRosco.baseUrl()
                        + "/api/v1/us-east-1/logs/some-status-id, Message: Server Error"));

    wmRosco.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/us-east-1/logs/some-status-id")));
  }

  @Test
  void lookupLogsRoscoNotFound() throws Exception {
    wmRosco.stubFor(
        WireMock.get(urlPathEqualTo("/api/v1/us-east-1/logs/some-status-id"))
            .willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())));

    webAppMockMvc
        .perform(
            get("/bakery/logs/us-east-1/some-status-id")
                .header(USER.getHeader(), USERNAME)
                .header(ACCOUNTS.getHeader(), ACCOUNT))
        .andDo(print())
        .andExpect(status().isNotFound())
        .andExpect(
            status()
                .reason(
                    "Status: 404, Method: GET, URL: "
                        + wmRosco.baseUrl()
                        + "/api/v1/us-east-1/logs/some-status-id, Message: Not Found"));

    wmRosco.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/us-east-1/logs/some-status-id")));
  }

  @Test
  void lookupLogsNoContent() throws Exception {
    // rosco returns 200 but with no logsContent
    wmRosco.stubFor(
        WireMock.get(urlPathEqualTo("/api/v1/us-east-1/logs/some-status-id"))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")));

    webAppMockMvc
        .perform(
            get("/bakery/logs/us-east-1/some-status-id")
                .header(USER.getHeader(), USERNAME)
                .header(ACCOUNTS.getHeader(), ACCOUNT))
        .andDo(print())
        .andExpect(status().isNotFound())
        .andExpect(status().reason("Bake logs not found."));

    wmRosco.verify(1, getRequestedFor(urlPathEqualTo("/api/v1/us-east-1/logs/some-status-id")));
  }
}
