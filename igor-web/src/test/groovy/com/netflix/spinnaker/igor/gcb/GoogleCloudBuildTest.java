/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.igor.gcb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.api.services.cloudbuild.v1.model.Build;
import com.google.api.services.cloudbuild.v1.model.BuildStep;
import com.google.api.services.cloudbuild.v1.model.Operation;
import com.netflix.spinnaker.igor.config.GoogleCloudBuildConfig;
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@RunWith(SpringRunner.class)
@AutoConfigureMockMvc(secure = false)
@EnableWebMvc
@SpringBootTest(classes = {
  GoogleCloudBuildConfig.class,
  GoogleCloudBuildController.class,
  GenericExceptionHandlers.class,
  WireMockConfig.class
})
@TestPropertySource(properties = {"spring.config.location=classpath:gcb/gcb-test.yml"})
public class GoogleCloudBuildTest {
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  @Qualifier("stubCloudBuildService")
  private WireMockServer stubCloudBuildService;

  private ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void missingAccountTest() throws Exception {
    String testBuild = objectMapper.writeValueAsString(buildRequest());
    mockMvc.perform(
      post("/gcb/builds/create/missing-account")
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .content(testBuild)
    ).andExpect(status().is(404));
  }

  @Test
  public void presentAccountTest() throws Exception {
    String buildRequest = objectMapper.writeValueAsString(buildRequest());
    String buildResponse = objectMapper.writeValueAsString(buildResponse());
    stubCloudBuildService.stubFor(
      WireMock
        .post(urlEqualTo("/v1/projects/spinnaker-gcb-test/builds"))
        .withHeader("Authorization", equalTo("Bearer test-token"))
        .withRequestBody(equalToJson(buildRequest))
        .willReturn(aResponse().withStatus(200).withBody(buildResponse))
    );

    mockMvc.perform(
      post("/gcb/builds/create/gcb-account")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .content(buildRequest)
    ).andExpect(status().is(200))
      .andExpect(content().json(buildResponse));

    assertThat(stubCloudBuildService.findUnmatchedRequests().getRequests()).isEmpty();
  }

  @Test
  public void listAccountTest() throws Exception {
    List<String> expectedAccounts = Collections.singletonList("gcb-account");
    String expectedResponse = objectMapper.writeValueAsString(expectedAccounts);

    mockMvc.perform(
      get("/gcb/accounts")
        .accept(MediaType.APPLICATION_JSON)
    ).andExpect(status().is(200))
      .andExpect(content().json(expectedResponse));

    assertThat(stubCloudBuildService.findUnmatchedRequests().getRequests()).isEmpty();
  }

  private Build buildRequest() {
    List<String> args = new ArrayList<>();
    args.add("echo");
    args.add("Hello, world!");

    BuildStep buildStep = new BuildStep().setArgs(args).setName("hello");
    return new Build().setSteps(Collections.singletonList(buildStep));
  }

  private Operation buildResponse() {
    Build build = buildRequest();
    build.setId("9f7a39db-b605-437f-aac1-f1ec3b798105");
    build.setStatus("QUEUED");
    build.setProjectId("spinnaker-gcb-test");
    build.setCreateTime("2019-03-26T16:00:08.659446379Z");

    Map<String, Object> metadata = new HashMap<>();
    metadata.put("@type", "type.googleapis.com/google.devtools.cloudbuild.v1.BuildOperationMetadata");
    metadata.put("build", build);

    Operation operation = new Operation();
    operation.setName("operations/build/spinnaker-gcb-test/operationid");
    operation.setMetadata(metadata);
    return operation;
  }
}
