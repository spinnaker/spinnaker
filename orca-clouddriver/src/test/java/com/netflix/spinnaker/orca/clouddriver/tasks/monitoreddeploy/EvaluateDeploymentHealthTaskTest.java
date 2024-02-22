/*
 * Copyright 2024 Salesforce, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.monitoreddeploy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.google.common.collect.Iterables;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.config.DeploymentMonitorDefinition;
import com.netflix.spinnaker.kork.test.log.MemoryAppender;
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.deploymentmonitor.DeploymentMonitorServiceProvider;
import com.netflix.spinnaker.orca.deploymentmonitor.models.DeploymentMonitorStageConfig;
import com.netflix.spinnaker.orca.deploymentmonitor.models.MonitoredDeployInternalStageData;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import retrofit.RestAdapter;
import retrofit.client.OkClient;

public class EvaluateDeploymentHealthTaskTest {

  private static final String MONITOR_ID = "monitorId";

  private static final String MONITOR_NAME = "monitorName";

  private static final String URL_PATH = "/deployment/evaluateHealth";

  private static final TaskResult RUNNING_WITH_ONE_ATTEMPT =
      TaskResult.builder(ExecutionStatus.RUNNING)
          .context(Map.of("deployMonitorHttpRetryCount", 1))
          .build();

  /**
   * Use this instead of annotating the class with @WireMockTest so there's a WireMock object
   * available for parameterized tests. Otherwise the arguments from e.g. MethodSource compete with
   * the WireMockRuntimeInfo argument that @WireMockTest provides.
   */
  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static List<DeploymentMonitorDefinition> deploymentMonitorDefinitions;

  private static ObjectMapper objectMapper = new ObjectMapper();

  private EvaluateDeploymentHealthTask evaluateDeploymentHealthTask;

  private StageExecutionImpl stage;

  private MemoryAppender memoryAppender;

  @BeforeAll
  static void setupOnce(WireMockRuntimeInfo wmRuntimeInfo) {
    DeploymentMonitorDefinition deploymentMonitorDefinition = new DeploymentMonitorDefinition();
    deploymentMonitorDefinition.setId(MONITOR_ID);
    deploymentMonitorDefinition.setName(MONITOR_NAME);
    deploymentMonitorDefinition.setBaseUrl(wmRuntimeInfo.getHttpBaseUrl());
    deploymentMonitorDefinitions = new ArrayList<>();
    deploymentMonitorDefinitions.add(deploymentMonitorDefinition);
  }

  @BeforeEach
  void setup(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());

    OkClient okClient = new OkClient();
    RestAdapter.LogLevel retrofitLogLevel = RestAdapter.LogLevel.NONE;

    DeploymentMonitorServiceProvider deploymentMonitorServiceProvider =
        new DeploymentMonitorServiceProvider(
            okClient,
            retrofitLogLevel,
            new SpinnakerRequestInterceptor(true),
            deploymentMonitorDefinitions);
    evaluateDeploymentHealthTask =
        new EvaluateDeploymentHealthTask(deploymentMonitorServiceProvider, new NoopRegistry());

    MonitoredDeployInternalStageData stageData = new MonitoredDeployInternalStageData();
    DeploymentMonitorStageConfig deploymentMonitorStageConfig = new DeploymentMonitorStageConfig();
    deploymentMonitorStageConfig.setId(MONITOR_ID);
    stageData.setDeploymentMonitor(deploymentMonitorStageConfig);
    Map<String, Object> contextMap = stageData.toContextMap();
    PipelineExecutionImpl pipeline = new PipelineExecutionImpl(PIPELINE, "orca");
    contextMap.put("application", pipeline.getApplication());
    stage = new StageExecutionImpl(pipeline, "evaluateDeploymentHealth", contextMap);

    memoryAppender = new MemoryAppender(EvaluateDeploymentHealthTask.class);
  }

  private static UUID simulateResponse(
      HttpStatus httpStatus, HttpHeaders httpHeaders, String body) {
    return wireMock
        .givenThat(
            WireMock.post(urlPathEqualTo(URL_PATH))
                .willReturn(
                    aResponse()
                        .withStatus(httpStatus.value())
                        .withStatusMessage(httpStatus.getReasonPhrase())
                        .withHeaders(httpHeaders)
                        .withBody(body)))
        .getId();
  }

  private static void simulateFault() {
    // choose an arbitrary fault that causes a network error / no response
    wireMock.givenThat(
        WireMock.post(urlPathEqualTo(URL_PATH))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
  }

  @Test
  void testWarningMessageWithNoResponse() {
    // given
    simulateFault();

    // when
    TaskResult result = evaluateDeploymentHealthTask.execute(stage);

    // then
    assertThat(result).isEqualTo(RUNNING_WITH_ONE_ATTEMPT);

    String expectedMessage =
        String.format(
            "HTTP Error encountered while talking to %s(%s)->%s%s, <NO RESPONSE>",
            MONITOR_NAME, MONITOR_ID, wireMock.baseUrl(), URL_PATH);
    List<String> warnings = memoryAppender.search(expectedMessage, Level.WARN);
    assertThat(warnings).hasSize(1);
  }

  @Test
  void testWarningMessageForConversionException() throws Exception {
    // given
    Map<String, Object> bogusEvaluateHealthResponseMap = Map.of("nextStep", "bogus");
    String bogusEvaluateHealthResponseString =
        objectMapper.writeValueAsString(bogusEvaluateHealthResponseMap);

    simulateResponse(HttpStatus.OK, HttpHeaders.noHeaders(), bogusEvaluateHealthResponseString);

    // when
    TaskResult result = evaluateDeploymentHealthTask.execute(stage);

    // then
    assertThat(result).isEqualTo(RUNNING_WITH_ONE_ATTEMPT);

    String expectedMessage =
        String.format(
            "HTTP Error encountered while talking to %s(%s)->%s%s, <NO RESPONSE>",
            MONITOR_NAME, MONITOR_ID, wireMock.baseUrl(), URL_PATH);
    List<String> warnings = memoryAppender.search(expectedMessage, Level.WARN);
    assertThat(warnings).hasSize(1);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("testResponses")
  void testWarningMessage(
      HttpStatus httpStatus, HttpHeaders httpHeaders, String body, String expectedBody) {
    // given
    UUID stubId = simulateResponse(httpStatus, httpHeaders, body);

    // when
    TaskResult result = evaluateDeploymentHealthTask.execute(stage);

    // then
    assertThat(result).isEqualTo(RUNNING_WITH_ONE_ATTEMPT);

    verifyLogMessage(stubId, httpStatus, httpHeaders, expectedBody);
  }

  private static Stream<Arguments> testResponses() throws Exception {
    HttpStatus httpStatus = HttpStatus.BAD_REQUEST; // arbitrary non-200 status

    Map<String, Object> responseMap = Map.of("responseKey", "responseValue");
    String jsonObject =
        objectMapper.writeValueAsString(responseMap); // an arbitrary non-empty json object
    String emptyJsonObject = objectMapper.writeValueAsString(Collections.emptyMap());

    List<String> responseList = List.of("listElement");
    String jsonArray =
        objectMapper.writeValueAsString(responseList); // an arbitrary non-empty json array

    HttpHeader httpHeader = new HttpHeader("arbitrary-header", "arbitrary-header-value");
    HttpHeaders httpHeaders = new HttpHeaders(httpHeader);

    String nonJsonResponse = "non-json response";

    HttpHeader httpHeader1 = new HttpHeader("arbitrary-header-1", "arbitrary-header-value-1");
    HttpHeader httpHeader2 = new HttpHeader("arbitrary-header-2", "arbitrary-header-value-2a");
    HttpHeader httpHeader3 = new HttpHeader("arbitrary-header-2", "arbitrary-header-value-2b");
    HttpHeader httpHeader4 = new HttpHeader("arbitrary-header-3", "arbitrary-header-value-3");
    HttpHeaders multipleHttpHeaders =
        new HttpHeaders(httpHeader1, httpHeader2, httpHeader3, httpHeader4);

    return Stream.of(
        arguments(
            named("json object", httpStatus), HttpHeaders.noHeaders(), jsonObject, jsonObject),
        arguments(
            named("empty json object", httpStatus),
            HttpHeaders.noHeaders(),
            emptyJsonObject,
            emptyJsonObject),
        arguments(named("json array", httpStatus), HttpHeaders.noHeaders(), jsonArray, ""),
        arguments(named("non json", httpStatus), httpHeaders, nonJsonResponse, ""),
        arguments(
            named("multiple headers", httpStatus), multipleHttpHeaders, jsonObject, jsonObject));
  }

  /**
   * Verify that the warning that MonitoredDeployBaseTask.execute logs when executeInternal throws a
   * SpinnakerServerException is as expected.
   *
   * @param stubId the wiremock stub id
   * @param httpStatus the http status to expect
   * @param httpHeaders the http headers to expect
   * @param expectedBody the body to expect
   */
  private void verifyLogMessage(
      UUID stubId, HttpStatus httpStatus, HttpHeaders httpHeaders, String expectedBody) {
    // WireMock inserts a number of headers, so it's difficult to search for an
    // exact string in the log.  Include enough to extract the relevant message,
    // and then do some parsing to assert on the bits we care about.
    String expectedMessage =
        String.format(
            "HTTP Error encountered while talking to %s(%s)->%s%s",
            MONITOR_NAME, MONITOR_ID, wireMock.baseUrl(), URL_PATH);
    List<String> warnings = memoryAppender.search(expectedMessage, Level.WARN);
    assertThat(warnings).hasSize(1);
    String warning = Iterables.getOnlyElement(warnings);

    // The first header that WireMock inserts is Matched-Stub-Id: <stub id>.
    // All of our headers come before that, so create the regex to only capture
    // ours.

    // headers: (.*)\n? matches when there are no headers, but doesn't match when there are
    // headers: (.*)\n matches when there is at least one header, but doesn't match when there are
    // none
    // headers: (.*) matches always, but then the expected header string needs an extra \n when
    // there's at least one header
    Pattern messagePattern =
        Pattern.compile(
            Pattern.quote("[WARN] " + expectedMessage)
                + ", status: ([^\n]*)\nheaders: (.*)Matched-Stub-Id: "
                + stubId
                + ".*response body: (.*)}",
            Pattern.DOTALL);
    Matcher matcher = messagePattern.matcher(warning);
    assertThat(matcher.matches()).isTrue();

    String statusInMessage = matcher.group(1);
    assertThat(statusInMessage)
        .isEqualTo(httpStatus.value() + " (" + httpStatus.getReasonPhrase() + ")");

    // Each header appears in the message on its own line, even when there are
    // multiple values for the same header.  So unfortunately
    //
    // assertThat(headersInMessage).isEqualTo(httpHeaders.toString());
    //
    // doesn't work, as httpHeaders.toString() is:
    //
    // arbitrary-header-1: [arbitrary-header-value-1]
    // arbitrary-header-2: [arbitrary-header-value-2a, arbitrary-header-value-2b]
    //
    // where the actual output is:
    //
    // arbitrary-header-1: arbitrary-header-value-1
    // arbitrary-header-2: arbitrary-header-value-2a
    // arbitrary-header-2: arbitrary-header-value-2b
    String expectedHeaderString =
        httpHeaders.all().stream().map(HttpHeader::toString).collect(Collectors.joining("\n"));
    if (!expectedHeaderString.isEmpty()) {
      expectedHeaderString = expectedHeaderString + "\n";
    }
    String headersInMessage = matcher.group(2);
    assertThat(headersInMessage).isEqualTo(expectedHeaderString);

    String bodyInMessage = matcher.group(3);
    assertThat(bodyInMessage).isEqualTo(expectedBody);
  }
}
