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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.api.services.cloudbuild.v1.model.Build;
import com.google.api.services.cloudbuild.v1.model.BuildOptions;
import com.google.api.services.cloudbuild.v1.model.BuildStep;
import com.google.api.services.cloudbuild.v1.model.BuildTrigger;
import com.google.api.services.cloudbuild.v1.model.ListBuildTriggersResponse;
import com.google.api.services.cloudbuild.v1.model.Operation;
import com.google.api.services.cloudbuild.v1.model.RepoSource;
import com.netflix.spinnaker.igor.RedisConfig;
import com.netflix.spinnaker.igor.config.LockManagerConfig;
import com.netflix.spinnaker.kork.web.exceptions.GenericExceptionHandlers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@EnableWebMvc
@SpringBootTest(
    classes = {
      GoogleCloudBuildConfig.class,
      GoogleCloudBuildController.class,
      RedisConfig.class,
      LockManagerConfig.class,
      GenericExceptionHandlers.class,
      GoogleCloudBuildTestConfig.class
    })
@TestPropertySource(properties = {"spring.config.location=classpath:gcb/gcb-test.yml"})
public class GoogleCloudBuildTest {
  @Autowired private MockMvc mockMvc;

  @Autowired
  @Qualifier("stubCloudBuildService")
  private WireMockServer stubCloudBuildService;

  private ObjectMapper objectMapper = new ObjectMapper();

  @TestConfiguration
  @EnableWebSecurity
  @Order(1)
  static class WebSecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity httpSecurity) throws Exception {
      httpSecurity.authorizeRequests().anyRequest().permitAll().and().csrf().disable();
    }
  }

  @Test
  public void missingAccountTest() throws Exception {
    String testBuild = objectMapper.writeValueAsString(buildRequest());
    mockMvc
        .perform(
            post("/gcb/builds/create/missing-account")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(testBuild))
        .andExpect(status().is(404));
  }

  @Test
  public void presentAccountTest() throws Exception {
    String buildRequest = objectMapper.writeValueAsString(buildRequest());
    String taggedBuild = objectMapper.writeValueAsString(taggedBuild());
    String buildResponse = objectMapper.writeValueAsString(buildResponse());
    String operationResponse = objectMapper.writeValueAsString(operationResponse());
    stubCloudBuildService.stubFor(
        WireMock.post(urlEqualTo("/v1/projects/spinnaker-gcb-test/builds"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withRequestBody(equalToJson(taggedBuild))
            .willReturn(aResponse().withStatus(200).withBody(operationResponse)));

    mockMvc
        .perform(
            post("/gcb/builds/create/gcb-account")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(buildRequest))
        .andExpect(status().is(200))
        .andExpect(content().json(buildResponse));

    assertThat(stubCloudBuildService.findUnmatchedRequests().getRequests()).isEmpty();
  }

  @Test
  public void updateBuildTest() throws Exception {
    String buildId = "f0fc7c14-6035-4e5c-bda1-4848a73af5b4";
    String working = "WORKING";
    String success = "SUCCESS";
    String queued = "QUEUED";

    Build workingBuild = buildRequest().setId(buildId).setStatus(working);
    mockMvc
        .perform(
            put(String.format("/gcb/builds/gcb-account/%s?status=%s", buildId, working))
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(workingBuild)))
        .andExpect(status().is(200));

    assertThat(stubCloudBuildService.findUnmatchedRequests().getRequests()).isEmpty();

    mockMvc
        .perform(
            get(String.format("/gcb/builds/gcb-account/%s", buildId))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is(200))
        .andExpect(content().json(objectMapper.writeValueAsString(workingBuild)));

    Build successfulBuild = buildRequest().setId(buildId).setStatus(success);
    mockMvc
        .perform(
            put(String.format("/gcb/builds/gcb-account/%s?status=%s", buildId, success))
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(successfulBuild)))
        .andExpect(status().is(200));

    assertThat(stubCloudBuildService.findUnmatchedRequests().getRequests()).isEmpty();

    mockMvc
        .perform(
            get(String.format("/gcb/builds/gcb-account/%s", buildId))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is(200))
        .andExpect(content().json(objectMapper.writeValueAsString(successfulBuild)));

    // Test that an out-of-order update back to "QUEUED" does not affect the cached value
    Build queuedBuild = buildRequest().setId(buildId).setStatus(queued);
    mockMvc
        .perform(
            put(String.format("/gcb/builds/gcb-account/%s?status=%s", buildId, queued))
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(queuedBuild)))
        .andExpect(status().is(200));

    assertThat(stubCloudBuildService.findUnmatchedRequests().getRequests()).isEmpty();

    mockMvc
        .perform(
            get(String.format("/gcb/builds/gcb-account/%s", buildId))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is(200))
        .andExpect(content().json(objectMapper.writeValueAsString(successfulBuild)));
  }

  @Test
  public void listAccountTest() throws Exception {
    List<String> expectedAccounts = Collections.singletonList("gcb-account");
    String expectedResponse = objectMapper.writeValueAsString(expectedAccounts);

    mockMvc
        .perform(get("/gcb/accounts").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is(200))
        .andExpect(content().json(expectedResponse));

    assertThat(stubCloudBuildService.findUnmatchedRequests().getRequests()).isEmpty();
  }

  @Test
  public void listTriggersTest() throws Exception {
    List<BuildTrigger> triggers = Arrays.asList(buildTrigger("trigger1"), buildTrigger("trigger2"));
    String listBuildTriggersResponseJson =
        objectMapper.writeValueAsString(listBuildTriggersResponse(triggers));
    String expectedTriggers = objectMapper.writeValueAsString(triggers);
    stubCloudBuildService.stubFor(
        WireMock.get(urlEqualTo("/v1/projects/spinnaker-gcb-test/triggers"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .willReturn(aResponse().withStatus(200).withBody(listBuildTriggersResponseJson)));

    mockMvc
        .perform(get("/gcb/triggers/gcb-account").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is(200))
        .andExpect(content().json(expectedTriggers));

    assertThat(stubCloudBuildService.findUnmatchedRequests().getRequests()).isEmpty();
  }

  @Test
  public void testListTrigersWorkWhenNoTrigerDefined() throws Exception {
    String emptyListResponse =
        objectMapper.writeValueAsString(listBuildTriggersResponse(Arrays.asList()));
    stubCloudBuildService.stubFor(
        WireMock.get(urlEqualTo("/v1/projects/spinnaker-gcb-test/triggers"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .willReturn(aResponse().withStatus(200).withBody(emptyListResponse)));

    mockMvc
        .perform(get("/gcb/triggers/gcb-account").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is(200))
        .andExpect(content().json("[]"));

    assertThat(stubCloudBuildService.findUnmatchedRequests().getRequests()).isEmpty();
  }

  @Test
  public void runTriggerWorksSuccessfullyTest() throws Exception {
    String buildResponse = objectMapper.writeValueAsString(buildResponse());
    String operationResponse = objectMapper.writeValueAsString(operationResponse());
    String repoSource = objectMapper.writeValueAsString(repoSource("master"));
    stubCloudBuildService.stubFor(
        WireMock.post(urlEqualTo("/v1/projects/spinnaker-gcb-test/triggers/my-id:run"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withRequestBody(equalToJson(repoSource))
            .willReturn(aResponse().withStatus(200).withBody(operationResponse)));

    mockMvc
        .perform(
            post("/gcb/triggers/gcb-account/my-id/run")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(repoSource))
        .andExpect(status().is(200))
        .andExpect(content().json(buildResponse));

    assertThat(stubCloudBuildService.findUnmatchedRequests().getRequests()).isEmpty();
  }

  @Test
  public void fallbackToPollingTest() throws Exception {
    String buildId = "f0fc7c14-6035-4e5c-bda1-4848a73af5b4";
    String working = "WORKING";

    Build workingBuild = buildRequest().setId(buildId).setStatus(working);

    stubCloudBuildService.stubFor(
        WireMock.get(
                urlEqualTo(String.format("/v1/projects/spinnaker-gcb-test/builds/%s", buildId)))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(objectMapper.writeValueAsString(workingBuild))));

    mockMvc
        .perform(
            get(String.format("/gcb/builds/gcb-account/%s", buildId))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is(200))
        .andExpect(content().json(objectMapper.writeValueAsString(workingBuild)));

    assertThat(stubCloudBuildService.findUnmatchedRequests().getRequests()).isEmpty();

    // The initial request should prime the cache, so we should get the same result back on re-try
    // without hitting
    // the GCB API again

    mockMvc
        .perform(
            get(String.format("/gcb/builds/gcb-account/%s", buildId))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is(200))
        .andExpect(content().json(objectMapper.writeValueAsString(workingBuild)));

    assertThat(stubCloudBuildService.findUnmatchedRequests().getRequests()).isEmpty();
  }

  private Build buildRequest() {
    List<String> args = new ArrayList<>();
    args.add("echo");
    args.add("Hello, world!");

    BuildStep buildStep = new BuildStep().setArgs(args).setName("hello");
    BuildOptions buildOptions = new BuildOptions().setLogging("LEGACY");

    return new Build().setSteps(Collections.singletonList(buildStep)).setOptions(buildOptions);
  }

  private Build taggedBuild() {
    return buildRequest().setTags(Collections.singletonList("started-by.spinnaker.io"));
  }

  private ListBuildTriggersResponse listBuildTriggersResponse(List<BuildTrigger> triggers) {
    return new ListBuildTriggersResponse().setTriggers(triggers);
  }

  private BuildTrigger buildTrigger(String description) {
    return new BuildTrigger()
        .setDescription(description)
        .setDisabled(false)
        .setId(UUID.randomUUID().toString());
  }

  private RepoSource repoSource(String branch) {
    return new RepoSource().setBranchName(branch);
  }

  private Build buildResponse() {
    Build build = taggedBuild();
    build.setId("9f7a39db-b605-437f-aac1-f1ec3b798105");
    build.setStatus("QUEUED");
    build.setProjectId("spinnaker-gcb-test");
    build.setCreateTime("2019-03-26T16:00:08.659446379Z");

    return build;
  }

  private Operation operationResponse() {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(
        "@type", "type.googleapis.com/google.devtools.cloudbuild.v1.BuildOperationMetadata");
    metadata.put("build", buildResponse());

    Operation operation = new Operation();
    operation.setName("operations/build/spinnaker-gcb-test/operationid");
    operation.setMetadata(metadata);
    return operation;
  }
}
