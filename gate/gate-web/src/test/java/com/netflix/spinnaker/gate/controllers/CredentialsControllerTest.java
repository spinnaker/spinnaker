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

package com.netflix.spinnaker.gate.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.netflix.spinnaker.kork.common.Header.ACCOUNTS;
import static com.netflix.spinnaker.kork.common.Header.REQUEST_ID;
import static com.netflix.spinnaker.kork.common.Header.USER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import java.util.List;
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
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "services.front50.applicationRefreshInitialDelayMs=3600000"
    })
public class CredentialsControllerTest {

  private static final String SUBMITTED_REQUEST_ID = "submitted-request-id";
  private static final String USERNAME = "some user";
  private static final String ACCOUNT = "my-account";
  private MockMvc webAppMockMvc;

  @RegisterExtension
  static WireMockExtension wmClouddriver =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired private WebApplicationContext webApplicationContext;

  @Autowired ObjectMapper objectMapper;

  /** To prevent periodic calls to service's /health endpoints */
  @MockBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  /**
   * This takes X-SPINNAKER-* headers from requests to gate and puts them in the MDC. This is
   * enabled when gate runs normally (by GateConfig), but needs explicit mention to function in
   * these tests.
   */
  @Autowired
  @Qualifier("authenticatedRequestFilter")
  private FilterRegistrationBean filterRegistrationBean;

  @DynamicPropertySource
  static void registerUrls(DynamicPropertyRegistry registry) {
    // Configure wiremock's random ports into gate
    System.out.println("wiremock clouddriver url: " + wmClouddriver.baseUrl());
    registry.add("services.clouddriver.base-url", wmClouddriver::baseUrl);
  }

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    webAppMockMvc =
        webAppContextSetup(webApplicationContext)
            .addFilters(filterRegistrationBean.getFilter())
            .build();

    wmClouddriver.stubFor(
        WireMock.get(urlEqualTo("/credentials?expand=true"))
            .willReturn(aResponse().withStatus(HttpStatus.OK.value()).withBody("[{}]")));
  }

  @Test
  void invokeAccountDefinitionsByType() throws Exception {
    ClouddriverService.AccountDefinition accountDefinition =
        new ClouddriverService.AccountDefinition();
    accountDefinition.setName("test");
    accountDefinition.setType("sometype");

    String accountDefinitionJson = objectMapper.writeValueAsString(List.of(accountDefinition));

    // mock clouddriver response
    wmClouddriver.stubFor(
        WireMock.get(urlPathEqualTo("/credentials/type/sometype"))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(accountDefinitionJson)));

    webAppMockMvc
        .perform(
            get("/credentials/type/sometype")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .header(USER.getHeader(), USERNAME)
                .header(REQUEST_ID.getHeader(), SUBMITTED_REQUEST_ID)
                .header(ACCOUNTS.getHeader(), ACCOUNT))
        .andDo(print())
        .andExpect(status().is2xxSuccessful())
        .andExpect(content().string(accountDefinitionJson))
        .andExpect(header().string(REQUEST_ID.getHeader(), SUBMITTED_REQUEST_ID));
  }
}
