/*
 * Copyright 2024 Harness, Inc.
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
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.gate.Main;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import com.netflix.spinnaker.gate.services.internal.EchoService;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = {Main.class})
@TestPropertySource("/application-echo.properties")
class EchoServiceTest {

  @Autowired EchoService echoService;

  /**
   * To prevent refreshing the applications cache, which involves calls to clouddriver and front50.
   */
  @MockBean ApplicationService applicationService;

  /** To prevent calls to clouddriver */
  @MockBean DefaultProviderLookupService defaultProviderLookupService;

  /** See https://wiremock.org/docs/junit-jupiter/#advanced-usage---programmatic */
  @RegisterExtension
  static WireMockExtension wmEcho =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @RegisterExtension
  static WireMockExtension wmClouddriver =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @RegisterExtension
  static WireMockExtension wmFront50 =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void registerUrls(DynamicPropertyRegistry registry) {
    // Configure wiremock's random ports into gate
    System.out.println("wiremock echo url: " + wmEcho.baseUrl());
    System.out.println("wiremock clouddriver url: " + wmClouddriver.baseUrl());
    System.out.println("wiremock front50 url: " + wmFront50.baseUrl());
    registry.add("services.echo.base-url", wmEcho::baseUrl);
    registry.add("services.clouddriver.base-url", wmClouddriver::baseUrl);
    registry.add("services.front50.base-url", wmFront50::baseUrl);
  }

  @BeforeAll
  static void setUp() throws IOException {
    wmClouddriver.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
    wmFront50.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(200)));
  }

  @Test
  void shouldNotOrderTheKeysWhenCallingEcho() throws Exception {

    // The response is arbitrary.  This test verifies the request body that gate
    // sends to echo.
    wmEcho.stubFor(
        post("/webhooks/git/github").willReturn(aResponse().withStatus(200).withBody("{}")));

    Map<String, Object> body = new HashMap<>();
    body.put("ref", "refs/heads/main");
    body.put("before", "ca7376e4b730f1f2878760abaeaed6c039fc5414");
    body.put("after", "c2420ce6e341ef0042f2e12591bdbe9eec29a032");
    body.put("id", 105648914);

    echoService.webhooks("git", "github", body);

    String expectedBody =
        "{\"ref\":\"refs/heads/main\",\"before\":\"ca7376e4b730f1f2878760abaeaed6c039fc5414\",\"after\":\"c2420ce6e341ef0042f2e12591bdbe9eec29a032\",\"id\":105648914}";

    wmEcho.verify(
        postRequestedFor(urlPathEqualTo("/webhooks/git/github"))
            .withRequestBody(equalTo(expectedBody)));
  }
}
