/*
 * Copyright 2022 Salesforce, Inc.
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
package com.netflix.spinnaker.rosco.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.netflix.spinnaker.kork.common.Header.USER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.rosco.Main;
import com.netflix.spinnaker.rosco.api.BakeStatus;
import com.netflix.spinnaker.rosco.executor.BakePoller;
import com.netflix.spinnaker.rosco.jobs.JobExecutor;
import com.netflix.spinnaker.rosco.jobs.JobRequest;
import com.netflix.spinnaker.rosco.manifests.helm.HelmBakeManifestRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = Main.class)
@TestPropertySource(properties = "spring.application.name = rosco")
class V2BakeryControllerWithClouddriverServiceTest {

  private MockMvc webAppMockMvc;

  /** See https://wiremock.org/docs/junit-jupiter/#advanced-usage---programmatic */
  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired private WebApplicationContext webApplicationContext;

  @MockBean BakePoller bakePoller;

  @Autowired ObjectMapper objectMapper;

  /** Prevent attempts to invoke a local binary (e.g. helm) */
  @MockBean JobExecutor jobExecutor;

  /**
   * This takes X-SPINNAKER-* headers from requests to rosco and puts them in the MDC. This is
   * enabled when rosco runs normally (by WebConfig), but needs explicit mention to function in
   * these tests.
   */
  @Autowired AuthenticatedRequestFilter authenticatedRequestFilter;

  private HelmBakeManifestRequest bakeManifestRequest;

  @DynamicPropertySource
  static void registerClouddriverBaseUrl(DynamicPropertyRegistry registry) {
    // Configure wiremock's random port into rosco
    System.out.println("wiremock server url: " + wm.baseUrl());
    registry.add("services.clouddriver.base-url", wm::baseUrl);
  }

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    webAppMockMvc =
        webAppContextSetup(webApplicationContext).addFilters(authenticatedRequestFilter).build();

    bakeManifestRequest = new HelmBakeManifestRequest();

    Artifact chartArtifact = Artifact.builder().name("test-artifact").version("3").build();
    bakeManifestRequest.setInputArtifacts(ImmutableList.of(chartArtifact));
    // Questionable that overrides isn't initialized to an empty map in
    // BakeManifestRequest since there's code that assumes it's not null.
    bakeManifestRequest.setOverrides(ImmutableMap.of());

    // Similarly for initializing outputName to an empty string.
    bakeManifestRequest.setOutputName("test-output-name");
  }

  @Test
  void testRequestHeadersToClouddriver() throws Exception {
    // Verify that headers in bake requests to rosco (e.g. X-SPINNAKER-*) make
    // it to the fetch artifacts request to clouddriver.

    // Mock a successful job execution.  Without a non-null status,
    // BakeManifestService.doBake never returns.
    String jobId = "test-job-id";
    BakeStatus bakeStatus = new BakeStatus();
    bakeStatus.setId(jobId);
    bakeStatus.setState(BakeStatus.State.COMPLETED);
    bakeStatus.setResult(BakeStatus.Result.SUCCESS);
    bakeStatus.setOutputContent("");
    when(jobExecutor.startJob(any(JobRequest.class))).thenReturn(jobId);
    when(jobExecutor.updateJob(jobId)).thenReturn(bakeStatus);

    // Simulate a successful response from clouddriver.  The actual response
    // isn't important since we're verifying headers in the request to
    // clouddriver.
    wm.stubFor(
        WireMock.put(urlMatching("/artifacts/fetch"))
            .willReturn(aResponse().withStatus(HttpStatus.OK.value()).withBody("success")));

    String userValue = "some user";
    webAppMockMvc
        .perform(
            post("/api/v2/manifest/bake/HELM2")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .characterEncoding(StandardCharsets.UTF_8.toString())
                .header(
                    USER.getHeader(), userValue) // arbitrary spinnaker (i.e. X-SPINNAKER-*) header
                .content(objectMapper.writeValueAsString(bakeManifestRequest)))
        .andDo(print())
        .andExpect(status().isOk());

    // verifing request to clouddriver contains header passing to rosco
    wm.verify(
        putRequestedFor(urlEqualTo("/artifacts/fetch"))
            .withHeader(USER.getHeader(), equalTo(userValue)));
  }
}
