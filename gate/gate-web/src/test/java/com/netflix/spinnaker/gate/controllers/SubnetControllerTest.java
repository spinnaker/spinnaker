/*
 * Copyright 2025 Salesforce, Inc.
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@SpringBootTest(classes = SubnetController.class)
class SubnetControllerTest {

  @Autowired SubnetController subnetController;

  @MockBean private ClouddriverServiceSelector clouddriverServiceSelector;

  private ClouddriverService clouddriverService;

  /** See https://wiremock.org/docs/junit-jupiter/#advanced-usage---programmatic */
  @RegisterExtension
  static WireMockExtension wmClouddriver =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setup(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    clouddriverService =
        new Retrofit.Builder()
            .baseUrl(wmClouddriver.baseUrl())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(ClouddriverService.class);

    when(clouddriverServiceSelector.select()).thenReturn(clouddriverService);
  }

  @Test
  void testAllByCloudProvider() throws Exception {
    // given
    String cloudProvider = "cloudProvider";
    List<Map> expectedSubnets = List.of(Map.of("subnet-a", "subnet-a-value"));

    wmClouddriver.stubFor(
        WireMock.get(urlPathEqualTo("/subnets/" + cloudProvider))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(objectMapper.writeValueAsString(expectedSubnets))));

    // then
    List<Map> actualSubnets = subnetController.allByCloudProvider(cloudProvider);

    // when
    assertThat(actualSubnets).isEqualTo(expectedSubnets);
    wmClouddriver.verify(getRequestedFor(urlPathEqualTo("/subnets/" + cloudProvider)));
  }
}
