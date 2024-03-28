/*
 * Copyright 2024 OpsMx, Inc.
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

package com.netflix.spinnaker.orca.mine.tasks;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.mine.MineService;
import com.netflix.spinnaker.orca.mine.pipeline.DeployCanaryStage;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatus;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.client.Response;
import retrofit.converter.JacksonConverter;
import retrofit.mime.TypedString;

public class RegisterCanaryTaskTest {

  private static MineService mineService;

  private static RegisterCanaryTask registerCanaryTask;

  private static StageExecution deployCanaryStage;

  private static ObjectMapper objectMapper = new ObjectMapper();

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @BeforeAll
  static void setupOnce(WireMockRuntimeInfo wmRuntimeInfo) {
    OkClient okClient = new OkClient();
    RestAdapter.LogLevel retrofitLogLevel = RestAdapter.LogLevel.NONE;

    mineService =
        new RestAdapter.Builder()
            .setEndpoint(wmRuntimeInfo.getHttpBaseUrl())
            .setClient(okClient)
            .setLogLevel(retrofitLogLevel)
            .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
            .setLog(new RetrofitSlf4jLog(MineService.class))
            .setConverter(new JacksonConverter(objectMapper))
            .build()
            .create(MineService.class);

    registerCanaryTask = new RegisterCanaryTask();
    registerCanaryTask.setMineService(mineService);
  }

  @BeforeEach
  public void setup() {
    var pipeline = PipelineExecutionImpl.newPipeline("foo");

    var canaryStageId = UUID.randomUUID().toString();
    var parentStageId = UUID.randomUUID().toString();

    deployCanaryStage = getDeployCanaryStage(pipeline, canaryStageId);
    deployCanaryStage.setParentStageId(parentStageId);
    var monitorCanaryStage =
        new StageExecutionImpl(pipeline, "monitorCanary", Collections.emptyMap());

    pipeline.getStages().addAll(List.of(deployCanaryStage, monitorCanaryStage));
  }

  private static void simulateFault(String url, String body, HttpStatus httpStatus) {
    wireMock.givenThat(
        WireMock.post(urlPathEqualTo(url))
            .willReturn(
                aResponse()
                    .withHeaders(HttpHeaders.noHeaders())
                    .withStatus(httpStatus.value())
                    .withBody(body)));
  }

  private static void simulateFault(String url, Fault fault) {
    wireMock.givenThat(WireMock.post(urlPathEqualTo(url)).willReturn(aResponse().withFault(fault)));
  }

  @Test
  public void verifyRegisterCanaryThrowsHttpError() throws Exception {

    var url = "https://mine.service.com/registerCanary";

    Response response =
        new Response(
            url,
            HttpStatus.NOT_ACCEPTABLE.value(),
            HttpStatus.NOT_ACCEPTABLE.getReasonPhrase(),
            List.of(),
            new TypedString("canaryId"));

    String errorResponseBody = objectMapper.writeValueAsString(response);

    // simulate error HTTP 406
    simulateFault("/registerCanary", errorResponseBody, HttpStatus.NOT_ACCEPTABLE);

    var canaryObject = (LinkedHashMap) deployCanaryStage.getContext().get("canary");
    canaryObject.put("application", "foo");

    var canaryConfig = (LinkedHashMap) canaryObject.get("canaryConfig");
    canaryConfig.put("name", deployCanaryStage.getExecution().getId());
    canaryConfig.put("application", "foo");

    // Format the canary data as per error log message
    var canary =
        Objects.toString(deployCanaryStage.getContext().get("canary"))
            .replace("{", "[")
            .replace("}", "]")
            .replace("=", ":");

    assertThatThrownBy(() -> registerCanaryTask.execute(deployCanaryStage))
        .hasMessageStartingWith(
            String.format(
                "Unable to register canary (executionId: %s, stageId: %s canary: %s)",
                deployCanaryStage.getExecution().getId(), deployCanaryStage.getId(), canary))
        .hasMessageContaining("response: [")
        .hasMessageContaining("reason:" + HttpStatus.NOT_ACCEPTABLE.getReasonPhrase())
        .hasMessageContaining("body:[bytes:Y2FuYXJ5SWQ=]")
        .hasMessageContaining("errorKind:HTTP")
        .hasMessageContaining("url:" + url)
        .hasMessageContaining("status:" + HttpStatus.NOT_ACCEPTABLE.value());
  }

  @Test
  public void verifyRegisterCanaryThrowsNetworkError() {

    // simulate network error
    simulateFault("/registerCanary", Fault.CONNECTION_RESET_BY_PEER);

    var canaryObject = (LinkedHashMap) deployCanaryStage.getContext().get("canary");
    canaryObject.put("application", "foo");

    var canaryConfig = (LinkedHashMap) canaryObject.get("canaryConfig");
    canaryConfig.put("name", deployCanaryStage.getExecution().getId());
    canaryConfig.put("application", "foo");

    // Format the canary data as per error log message
    var canary =
        Objects.toString(deployCanaryStage.getContext().get("canary"))
            .replace("{", "[")
            .replace("}", "]")
            .replace("=", ":");

    String errorResponseBody = "[status:null, errorKind:NETWORK]";

    assertThatThrownBy(() -> registerCanaryTask.execute(deployCanaryStage))
        .hasMessage(
            String.format(
                "Unable to register canary (executionId: %s, stageId: %s canary: %s), response: %s",
                deployCanaryStage.getExecution().getId(),
                deployCanaryStage.getId(),
                canary,
                errorResponseBody));
  }

  /*
   * Populate Register canary stage execution test data,
   * {@link LinkedHashMap} is used to maintain the insertion order , so the assertions will be much simpler
   * */
  @NotNull
  private static StageExecutionImpl getDeployCanaryStage(
      PipelineExecutionImpl pipeline, String canaryStageId) {
    return new StageExecutionImpl(
        pipeline,
        DeployCanaryStage.PIPELINE_CONFIG_TYPE,
        new LinkedHashMap<>(
            Map.of(
                "canaryStageId",
                canaryStageId,
                "account",
                "test",
                "canary",
                new LinkedHashMap<>(
                    Map.of(
                        "owner",
                        new LinkedHashMap<>(
                            Map.of("name", "cfieber", "email", "cfieber@netflix.com")),
                        "watchers",
                        Collections.emptyList(),
                        "canaryConfig",
                        new LinkedHashMap<>(
                            Map.of(
                                "lifetimeHours",
                                1,
                                "combinedCanaryResultStrategy",
                                "LOWEST",
                                "canarySuccessCriteria",
                                new LinkedHashMap<>(Map.of("canaryResultScore", 95)),
                                "canaryHealthCheckHandler",
                                new LinkedHashMap<>(
                                    Map.of(
                                        "minimumCanaryResultScore",
                                        75,
                                        "@class",
                                        "com.netflix.spinnaker.mine.CanaryResultHealthCheckHandler")),
                                "canaryAnalysisConfig",
                                new LinkedHashMap<>(
                                    Map.of(
                                        "name",
                                        "beans",
                                        "beginCanaryAnalysisAfterMins",
                                        5,
                                        "notificationHours",
                                        List.of(1, 2),
                                        "canaryAnalysisIntervalMins",
                                        15)))))))));
  }
}
