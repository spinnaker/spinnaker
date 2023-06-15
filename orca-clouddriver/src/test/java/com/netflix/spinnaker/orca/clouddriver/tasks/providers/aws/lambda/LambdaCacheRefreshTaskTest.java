/*
 * Copyright 2021 Armory, LLC
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.config.LambdaConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaCacheRefreshInput;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import ru.lanwen.wiremock.ext.WiremockResolver;
import ru.lanwen.wiremock.ext.WiremockUriResolver;

@ExtendWith({WiremockResolver.class, WiremockUriResolver.class})
public class LambdaCacheRefreshTaskTest {

  WireMockServer wireMockServer;

  @InjectMocks private LambdaCacheRefreshTask lambdaCacheRefreshTask;

  @Mock private CloudDriverConfigurationProperties propsMock;

  @Mock private StageExecution stageExecution;

  @Mock private PipelineExecution pipelineExecution;

  @Mock private LambdaCloudDriverUtils lambdaCloudDriverUtilsMock;

  @Mock private LambdaConfigurationProperties config;

  @BeforeEach
  void init(
      @WiremockResolver.Wiremock WireMockServer wireMockServer,
      @WiremockUriResolver.WiremockUri String uri) {
    this.wireMockServer = wireMockServer;
    MockitoAnnotations.initMocks(this);
    Mockito.when(propsMock.getCloudDriverBaseUrl()).thenReturn(uri);
    pipelineExecution.setApplication("lambdaApp");
    Mockito.when(stageExecution.getExecution()).thenReturn(pipelineExecution);

    LambdaCacheRefreshInput inp = LambdaCacheRefreshInput.builder().account("account").build();

    Mockito.when(stageExecution.getContext()).thenReturn(new HashMap<>());
    Mockito.when(lambdaCloudDriverUtilsMock.getInput(Mockito.any(), Mockito.any())).thenReturn(inp);
    Mockito.when(lambdaCloudDriverUtilsMock.asString(Mockito.any()))
        .thenReturn("LambdaCacheRefreshInput");
    Mockito.when(lambdaCloudDriverUtilsMock.buildHeaders())
        .thenReturn(Headers.of("Content-Disposition", "form-data; name=\"fs_exp\""));

    String responseDefinitionBuilderJson =
        "{\"cachedIdentifiersByType\":{\"onDemand\":[\"123456789\"]}}";
    ResponseDefinitionBuilder mockResponse =
        new ResponseDefinitionBuilder().withStatus(202).withBody(responseDefinitionBuilderJson);

    this.wireMockServer.stubFor(WireMock.post("/cache/aws/function").willReturn(mockResponse));
  }

  @Test
  public void execute_ShouldForceCacheRefreshAndGet200Code_SUCCEDED() {
    ResponseDefinitionBuilder mockResponse =
        new ResponseDefinitionBuilder().withStatus(200).withBody("Success");

    wireMockServer.stubFor(WireMock.post("/cache/aws/function").willReturn(mockResponse));
    assertEquals(
        ExecutionStatus.SUCCEEDED, lambdaCacheRefreshTask.execute(stageExecution).getStatus());
  }

  @Test
  public void execute_ShouldWaitForCacheToComplete_CachingShouldBeCompleted_SUCCEEDED()
      throws JsonProcessingException {
    Map<String, Object> map = new HashMap<>();
    map.put("processedCount", 1);
    // increase 15 seconds
    map.put("cacheTime", System.currentTimeMillis() + 15 * 1000);
    String getFromCloudDriverJson = new ObjectMapper().writeValueAsString(List.of(map));
    Mockito.when(lambdaCloudDriverUtilsMock.getFromCloudDriver(Mockito.any()))
        .thenReturn(getFromCloudDriverJson);
    assertEquals(
        ExecutionStatus.SUCCEEDED, lambdaCacheRefreshTask.execute(stageExecution).getStatus());
  }

  @Test
  public void
      forceCacheRefresh_waitForCacheToComplete_NotFoundAndThenCachingShouldBeCompleted_SUCCEEDED()
          throws JsonProcessingException {
    Map<String, Object> mapSecondCall = new HashMap<>();
    mapSecondCall.put("processedCount", 1);
    // increase 15 seconds
    mapSecondCall.put("cacheTime", System.currentTimeMillis() + 15 * 1000);
    String secondCallJson = new ObjectMapper().writeValueAsString(List.of(mapSecondCall));

    Mockito.when(lambdaCloudDriverUtilsMock.getFromCloudDriver(Mockito.any()))
        .thenReturn("[]")
        .thenReturn(secondCallJson);
    assertEquals(
        ExecutionStatus.SUCCEEDED, lambdaCacheRefreshTask.execute(stageExecution).getStatus());
  }

  @Test
  public void
      forceCacheRefresh_waitForCacheToComplete_ShouldRetryAndThenCachingShouldBeCompleted_SUCCEEDED()
          throws JsonProcessingException {
    Map<String, Object> mapSecondCall = new HashMap<>();
    mapSecondCall.put("processedCount", 1);
    mapSecondCall.put("cacheTime", System.currentTimeMillis() + 15 * 1000);
    String secondCallJson = new ObjectMapper().writeValueAsString(List.of(mapSecondCall));

    Mockito.when(lambdaCloudDriverUtilsMock.getFromCloudDriver(Mockito.any()))
        .thenReturn("[{\"processedCount\":0}]")
        .thenReturn(secondCallJson);
    assertEquals(
        ExecutionStatus.SUCCEEDED, lambdaCacheRefreshTask.execute(stageExecution).getStatus());
  }
}
